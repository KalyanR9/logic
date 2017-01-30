package hydrograph.engine.spark.components

import hydrograph.engine.core.component.entity.JoinEntity
import hydrograph.engine.spark.components.base.OperationComponentBase
import hydrograph.engine.spark.components.platform.BaseComponentParams
import hydrograph.engine.spark.components.utils._
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.{ Column, DataFrame }
import scala.collection.mutable.ListBuffer

/**
 * Created by gurdits on 10/18/2016.
 *
 */

class JoinComponentTwoPasses(joinEntity: JoinEntity, componentsParams: BaseComponentParams) extends OperationComponentBase with Serializable {

  override def createComponent(): Map[String, DataFrame] = {

    val joinUtils = new JoinUtils(joinEntity, componentsParams)
    val joinOperations = joinUtils.prepareJoinOperation()

    val passthroughFields = joinUtils.getPassthroughFields()
    val mapFields = joinUtils.getMapFields()
    val copyOfInSocketFields = joinUtils.getCopyOfInSocketFields()

    val joinOperationsSorted = joinOperations.sortBy(j => (j.recordRequired, !j.unused)).reverse

    join(joinOperationsSorted, passthroughFields, mapFields, copyOfInSocketFields)
  }

  def join(joinOperations: Array[JoinOperation], passthroughFields: List[(String, String)], mapFields: List[(String, String)], copyOfInSocketFields: List[(String, String)]): Map[String, DataFrame] = {

    var outMap = Map[String, DataFrame]()

    val requiredUnusedJoinOps = joinOperations.filter(j => ((j.recordRequired == true) && (j.unused == true)))
    val requiredUnusedInputs = requiredUnusedJoinOps.map(j => j.inSocketId)
    var requiredUnusedProcessedInputs = ListBuffer[String]()

    def dfJoin(headJoin: JoinOperation, tailJoins: Array[JoinOperation]): JoinOperation = {

      if (tailJoins.isEmpty) {
        headJoin
      } else if ((headJoin.recordRequired == false) && (tailJoins.head.recordRequired == false)) {
        dfJoin(fullOuterJoin(headJoin, tailJoins.head), tailJoins.tail)
      } else if ((headJoin.recordRequired == true) && (tailJoins.head.recordRequired == false) && (tailJoins.head.unused == false)) {
        dfJoin(leftOuterJoin(headJoin, tailJoins.head), tailJoins.tail)
      } else if ((headJoin.recordRequired == true) && (tailJoins.head.recordRequired == false) && (tailJoins.head.unused == true)) {
        dfJoin(leftOuterJoinForUnused(headJoin, tailJoins.head), tailJoins.tail)
      } else if ((headJoin.recordRequired == true) && (headJoin.unused == false) && (tailJoins.head.recordRequired == true) && (tailJoins.head.unused == false)) {
        dfJoin(innerJoin(headJoin, tailJoins.head), tailJoins.tail)
      } else {
        dfJoin(innerJoinForUnused(headJoin, tailJoins.head), tailJoins.tail)
      }

    }

    def innerJoin(lhs: JoinOperation, rhs: JoinOperation): JoinOperation = {
      val rhsModified = getJoinOpWithPrefixAdded(rhs, rhs.inSocketId)

      val lhsDF = lhs.dataFrame
      val rhsDF = rhsModified.dataFrame
      val lhsKeys = lhs.keyFields
      val rhsKeys = rhsModified.keyFields

      val joinedDF = lhsDF.join(rhsDF, createJoinKey(lhsKeys, rhsKeys), "inner")

      JoinOperation("join", "in", joinedDF, lhsKeys, false, true, lhs.outSocketId, "")
    }

    def leftOuterJoin(lhs: JoinOperation, rhs: JoinOperation): JoinOperation = {
      val rhsModified = getJoinOpWithPrefixAdded(rhs, rhs.inSocketId)

      val lhsDF = lhs.dataFrame
      val rhsDF = rhsModified.dataFrame
      val lhsKeys = lhs.keyFields
      val rhsKeys = rhsModified.keyFields

      val joinedDF = lhsDF.join(rhsDF, createJoinKey(lhsKeys, rhsKeys), "leftouter")

      JoinOperation("join", "in", joinedDF, lhsKeys, false, true, lhs.outSocketId, "")
    }

    def fullOuterJoin(lhs: JoinOperation, rhs: JoinOperation): JoinOperation = {
      val rhsModified = getJoinOpWithPrefixAdded(rhs, rhs.inSocketId)

      val lhsDF = lhs.dataFrame
      val rhsDF = rhsModified.dataFrame
      val lhsKeys = lhs.keyFields
      val rhsKeys = rhsModified.keyFields

      val joinedDF = lhsDF.join(rhsDF, createJoinKey(lhsKeys, rhsKeys), "outer")

      val blankDF = joinedDF.filter("false")
      val blankDF_lhs = blankDF.select(convertStructFieldsTOString(lhsDF.schema): _*)
      val blankDF_rhs = blankDF.select(convertStructFieldsTOString(rhsDF.schema): _*)

      val blankDF_lhs_prefixRemoved = blankDF_lhs.select(blankDF_lhs.columns.map(c => col(c).as(c.replaceFirst(lhs.inSocketId + "_", ""))): _*)
      val blankDF_rhs_prefixRemoved = blankDF_rhs.select(blankDF_rhs.columns.map(c => col(c).as(c.replaceFirst(rhs.inSocketId + "_", ""))): _*)

      if (lhs.unused) (outMap += (lhs.unusedSocketId -> blankDF_lhs_prefixRemoved))
      if (rhs.unused) (outMap += (rhs.unusedSocketId -> blankDF_rhs_prefixRemoved))

      JoinOperation("join", "in", joinedDF, lhsKeys, false, false, lhs.outSocketId, "")
    }

    def innerJoinForUnused(lhs: JoinOperation, rhs: JoinOperation): JoinOperation = {

      val outJoinOp = innerJoin(lhs, rhs)

      requiredUnusedProcessedInputs += lhs.inSocketId
      requiredUnusedProcessedInputs += rhs.inSocketId

      //Check if all required-unused inputs have been processed
      if (requiredUnusedProcessedInputs.containsAll(requiredUnusedInputs)) {
        collectUnusedDFs(outJoinOp)
      }

      outJoinOp
    }

    def collectUnusedDFs(outJoinOp: JoinOperation): Unit = {

      val lhsDF = outJoinOp.dataFrame.withColumn("input1", lit(1))
      val lhsKeys = outJoinOp.keyFields

      requiredUnusedJoinOps.foreach(j => {
        val joinedDF = lhsDF.join(j.dataFrame, createJoinKey(lhsKeys, j.keyFields), "outer")
        val unusedDF = joinedDF.filter("(input1 is null)")

        val unusedDF1 = unusedDF.select(j.dataFrame.columns.map(c => col(c)): _*)

        outMap += (j.unusedSocketId -> unusedDF1)
      })
    }

    def leftOuterJoinForUnused(lhs: JoinOperation, rhs: JoinOperation): JoinOperation = {
      val rhsModified = getJoinOpWithPrefixAdded(rhs, rhs.inSocketId)

      val lhsDF = lhs.dataFrame
      val rhsDF = rhsModified.dataFrame
      val lhsKeys = lhs.keyFields
      val rhsKeys = rhsModified.keyFields

      val joinedDF = lhsDF.withColumn("input1", lit(1)).join(rhsDF.withColumn("input2", lit(1)), createJoinKey(lhsKeys, rhsKeys), "outer")

      val unusedDF_rhs = joinedDF.filter("(input1 is null) and (input2 == 1)").select(convertStructFieldsTOString(rhsDF.schema): _*)

      val unusedDF_rhs_prefixRemoved = unusedDF_rhs.select(unusedDF_rhs.columns.map(c => col(c).as(c.replaceFirst(rhs.inSocketId + "_", ""))): _*)

      val outputDF = joinedDF.filter("(input1 == 1)").select(convertStructFieldsTOString(lhsDF.schema) ++ convertStructFieldsTOString(rhsDF.schema): _*)

      if (rhs.unused) (outMap += (rhs.unusedSocketId -> unusedDF_rhs_prefixRemoved))

      JoinOperation("join", "in", outputDF, lhsKeys, false, true, lhs.outSocketId, "")
    }

    def getJoinOpWithPrefixAdded(joinOp: JoinOperation, prefix: String): JoinOperation = {
      val originalDF = joinOp.dataFrame
      val modifiedDF = originalDF.select(originalDF.columns.map { c => col(c).as(prefix + "_" + c) }: _*)

      val originalKeys = joinOp.keyFields
      val modifiedKeys = originalKeys.map { colName => prefix + "_" + colName }

      JoinOperation(joinOp.compID, joinOp.inSocketId, modifiedDF, modifiedKeys, joinOp.unused, joinOp.recordRequired, joinOp.outSocketId, joinOp.unusedSocketId)
    }

    def getDFWithRequiredFields(df: DataFrame): DataFrame = {

      val mergedFields = (passthroughFields ++ mapFields ++ copyOfInSocketFields).distinct
      df.select(mergedFields.map(field => col(field._1).as(field._2)): _*)

    }

    val headJoinOp = getJoinOpWithPrefixAdded(joinOperations.head, joinOperations.head.inSocketId)

    val outputJoin = dfJoin(headJoinOp, joinOperations.tail)

    val outputResultDF = getDFWithRequiredFields(outputJoin.dataFrame)

    outMap += (joinOperations.head.outSocketId -> outputResultDF)

    outMap

  }

  def convertStructFieldsTOString(structType: StructType): Array[Column] = {
    val inputColumns = new Array[Column](structType.length)
    structType.zipWithIndex.foreach {
      case (sf, i) =>
        inputColumns(i) = col(sf.name)
    }
    inputColumns
  }

  def createJoinKey(lhsKeys: Array[String], rhsKeys: Array[String]): Column = (lhsKeys, rhsKeys) match {
    case (l, r) if l.length != r.length => sys.error("key fields should be same")
    case (l, r) if r.tail.length == 0   => return col(l.head) === col(r.head)
    case (l, r)                         => return (col(l.head) === col(r.head)).&&(createJoinKey(l.tail, r.tail))
  }

}
