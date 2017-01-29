package hydrograph.engine.spark.components

import org.apache.spark.sql._
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{StringType, StructField, StructType}
import scala.collection.mutable.ListBuffer

object JoinOption2 {

  def main(args: Array[String]): Unit = {
    val spark = SparkSession
      .builder()
      .appName("Spark Join").master("yarn")
      .config("spark.sql.warehouse.dir", "file:///tmp")
      .config("spark.sql.crossJoin.enabled", "true")
      .config("spark.sql.shuffle.partitions", 100)
      .getOrCreate()
    println("===============================Biegin JoinOption1=====================")
    val inputFilePath = "testData/input/parquet1.txt"
    val outPath = "testData/output/u"
    val unused1Path = "testData/output/u1"
    val unused2Path = "testData/output/u2"
    val unused3Path = "testData/output/u3"

    val df1 = spark.read.schema(makeSchema("df1")).csv(inputFilePath)
    val df2 = spark.read.schema(makeSchema("df2")).csv(inputFilePath)
    val df3 = spark.read.schema(makeSchema("df3")).csv(inputFilePath)
    val df4 = spark.read.schema(makeSchema("df4")).csv(inputFilePath)
    val df5 = spark.read.schema(makeSchema("df5")).csv(inputFilePath)

    println("===============================Start-JoinOption2=====================")

    val innerJoin = df1.join(df2, col("df1_col1") === col("df2_col1"), "inner")
    val innerJoin1 = innerJoin.join(df3, col("df3_col1") === col("df1_col1"), "inner")
    val innerJoin2 = innerJoin1.join(df4, col("df4_col1") === col("df1_col1"), "inner")
    val innerJoin3 = innerJoin2.join(df5, col("df5_col1") === col("df1_col1"), "inner")

    val unusedDF2 = joinForUnused(df1, "df2_col1", df2, "df1_col1")
    val unusedDF3 = joinForUnused(innerJoin, "df3_col1", df3, "df2_col1")
    val unusedDF4 = joinForUnused(innerJoin1, "df4_col1", df4, "df3_col1")
    val unusedDF5 = joinForUnused(innerJoin2, "df5_col1", df5, "df4_col1")

    val dff = innerJoin.join(innerJoin1).drop("df1_col1", "df2_col2", "df2_col1", "df1_col2").join(innerJoin2.join(innerJoin3)).drop("df1_col1", "df3_col1", "df2_col2", "df4_col2", "df3_col2", "df2_col1", "df1_col2", "df4_col1")

    println("===============================End=====================")

    dff.write.mode(SaveMode.Overwrite).csv(outPath)
    unusedDF2.write.mode(SaveMode.Overwrite).csv(unused1Path)
    unusedDF3.write.mode(SaveMode.Overwrite).csv(unused2Path)
    unusedDF4.write.mode(SaveMode.Overwrite).csv(unused3Path)
    println("===============================complete JoinOption2=====================")
  }

  def makeSchema(prefix: String): StructType = {
    var fields = ListBuffer[StructField]()
    val range = (1 to 2)
    range.foreach { i => fields += StructField(prefix + "_" + "col" + i, StringType, true) }
    StructType(fields)
  }

  def joinForUnused(lhsDF: DataFrame, lhsKey: String, rhsDF: DataFrame, rhsKey: String): DataFrame = {
    val modifiedLhsDF = lhsDF.withColumn("input1", lit(1))
    val modifiedRhsDF = rhsDF.withColumn("input2", lit(1))

    val joinedDF = modifiedLhsDF.join(modifiedRhsDF, col(lhsKey) === col(rhsKey), "outer")
    val unusedDF = joinedDF.select(lhsDF.columns.map(c => col(c)): _*).filter("(input1 == 1) and (input2 is null)")

    unusedDF
  }

}
