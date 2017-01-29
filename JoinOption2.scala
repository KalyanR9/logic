package hydrograph.engine.spark.components

import org.apache.spark.sql._
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{ StringType, StructField, StructType }
import scala.collection.mutable.ListBuffer

object JoinOption2 {

  def main(args: Array[String]): Unit = {
    val spark = SparkSession
      .builder()
      .appName("Spark Join").master("yarn")
      .config("spark.sql.warehouse.dir", "file:///tmp")
      .config("spark.sql.shuffle.partitions", 100)
      .getOrCreate()
    println("===============================Begin JoinOption1=====================")
    val inputFilePath = "testData/input/"
    val outPath = "testData/output/out"
    val unused2Path = "testData/output/unused2"
    val unused3Path = "testData/output/unused3"
    val unused4Path = "testData/output/unused4"

    val df1 = spark.read.schema(makeSchema("df1")).csv(inputFilePath + "file1.txt")
    val df2 = spark.read.schema(makeSchema("df2")).csv(inputFilePath + "file2.txt")
    val df3 = spark.read.schema(makeSchema("df3")).csv(inputFilePath + "file3.txt")
    val df4 = spark.read.schema(makeSchema("df4")).csv(inputFilePath + "file4.txt")
    val df5 = spark.read.schema(makeSchema("df5")).csv(inputFilePath + "file5.txt")

    var unusedDF2: DataFrame = null
    var unusedDF3: DataFrame = null
    var unusedDF4: DataFrame = null

    val partialUnusedList = ListBuffer[(String, DataFrame)]()

    def innerJoinForUnused(lhsDF: DataFrame, lhsKey: String, rhsDF: DataFrame, rhsKey: String): DataFrame = {
      val modifiedLhsDF = lhsDF.withColumn("input1", lit(1))
      val modifiedRhsDF = rhsDF.withColumn("input2", lit(1))

      val joinedDF = modifiedLhsDF.join(modifiedRhsDF, col(lhsKey) === col(rhsKey), "outer")
      val outDF = joinedDF.filter("(input1 == 1) and (input2 == 1)").drop("input1", "input2")
      val unusedDF = joinedDF.filter("(input1 is null) or (input2 is null)")
      
      if (lhsDF.columns.contains("df2_col1"))
      partialUnusedList += ("unused2", unusedDF.select(/*df2 columns*/).filter("input1 == 1"))
      
      if (lhsDF.columns.contains("df3_col1"))
      partialUnusedList += ("unused3", unusedDF.select(/*df3 columns*/).filter("input1 == 1"))
      
      if (lhsDF.columns.contains("df4_col1"))
      partialUnusedList += ("unused4", unusedDF.select(/*df4 columns*/).filter("input1 == 1"))
      
      if (rhsDF.columns.contains("df2_col1"))
      partialUnusedList += ("unused2", unusedDF.select(/*df2 columns*/).filter("input2 == 1"))
      
      if (rhsDF.columns.contains("df3_col1"))
      partialUnusedList += ("unused3", unusedDF.select(/*df3 columns*/).filter("input2 == 1"))
      
      if (rhsDF.columns.contains("df4_col1"))
      partialUnusedList += ("unused4", unusedDF.select(/*df4 columns*/).filter("input2 == 1"))
        
      outDF
    }
    
    def unionPartialUnused(): Unit = {

      val unused2Partials = partialUnusedList.filter(pair => (pair._1 == "unused2")).map(pair => pair._2)
      unusedDF2 = unused2Partials.reduce(_ union _)

      val unused3Partials = partialUnusedList.filter(pair => (pair._1 == "unused3")).map(pair => pair._2)
      unusedDF3 = unused3Partials.reduce(_ union _)

      val unused4Partials = partialUnusedList.filter(pair => (pair._1 == "unused4")).map(pair => pair._2)
      unusedDF4 = unused4Partials.reduce(_ union _)
    }

    println("===============================Start-JoinOption2=====================")

    val partialOut1 = df1.join(df5, col("df1_col1") === col("df5_col1"), "inner")

    val partialOut2 = innerJoinForUnused(partialOut1, "df1_col1", df2, "df2_col1")
    val partialOut3 = innerJoinForUnused(partialOut2, "df1_col1", df3, "df3_col1")
    val outDF = innerJoinForUnused(partialOut3, "df1_col1", df4, "df4_col1")

    unionPartialUnused()

    println("===============================End=====================")

    outDF.write.mode(SaveMode.Overwrite).csv(outPath)
    unusedDF2.write.mode(SaveMode.Overwrite).csv(unused2Path)
    unusedDF3.write.mode(SaveMode.Overwrite).csv(unused3Path)
    unusedDF4.write.mode(SaveMode.Overwrite).csv(unused4Path)
    println("===============================complete JoinOption2=====================")
  }

  def makeSchema(prefix: String): StructType = {
    var fields = ListBuffer[StructField]()
    val range = (1 to 20)
    range.foreach { i => fields += StructField(prefix + "_" + "col" + i, StringType, true) }
    StructType(fields)
  }

}
