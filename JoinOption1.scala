package hydrograph.engine.spark.components

import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{StringType, StructField, StructType}
import org.apache.spark.sql.{DataFrame, SaveMode, SparkSession}

import scala.collection.mutable.ListBuffer

object JoinOption1 {

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

    println("===============================Start=====================")

    val outDF = df1.join(df2, col("df1_col1") === col("df2_col1"), "inner")
      .join(df3, col("df1_col1") === col("df3_col1"), "inner")
      .join(df4, col("df1_col1") === col("df4_col1"), "inner")
      .join(df5, col("df1_col1") === col("df5_col1"), "inner")


    val outDF1 = outDF.select(outDF.columns.map { c => col(c).as("out_" + c) }: _*)

    val unused2DF = getUnusedDF(df2, "df2_col1", outDF1, "out_df1_col1")
    val unused3DF = getUnusedDF(df3, "df3_col1", outDF1, "out_df1_col1")
    val unused4DF = getUnusedDF(df4, "df4_col1", outDF1, "out_df1_col1")

    println("===============================End=====================")

    outDF.write.mode(SaveMode.Overwrite).csv(outPath)
    unused2DF.write.mode(SaveMode.Overwrite).csv(unused2Path)
    unused3DF.write.mode(SaveMode.Overwrite).csv(unused3Path)
    unused4DF.write.mode(SaveMode.Overwrite).csv(unused4Path)
    println("===============================complete JoinOption1=====================")
  }

  def makeSchema(prefix: String): StructType = {
    var fields = ListBuffer[StructField]()
    val range = (1 to 20)
    range.foreach { i => fields += StructField(prefix + "_" + "col" + i, StringType, true) }
    StructType(fields)
  }

  def getUnusedDF(lhsDF: DataFrame, lhsKey: String, rhsDF: DataFrame, rhsKey: String): DataFrame = {
    val modifiedLhsDF = lhsDF.withColumn("input1", lit(1))
    val modifiedRhsDF = rhsDF.withColumn("input2", lit(1))

    val joinedDF = modifiedLhsDF.join(modifiedRhsDF, col(lhsKey) === col(rhsKey), "outer")
    val unusedDF = joinedDF.select(lhsDF.columns.map(c => col(c)): _*).filter("(input1 == 1) and (input2 is null)")

    unusedDF
  }

}
