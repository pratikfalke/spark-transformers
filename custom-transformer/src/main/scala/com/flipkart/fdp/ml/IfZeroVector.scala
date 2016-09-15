package com.flipkart.fdp.ml

import org.apache.spark.ml.Transformer
import org.apache.spark.ml.param.{Params, Param, ParamMap}
import org.apache.spark.ml.util.Identifiable
import org.apache.spark.mllib.linalg.{DenseVector, SparseVector, Vector, VectorUDT, Vectors}
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.StructType


trait IfZeroVectorParams extends Params {
  final val inputCol: Param[String] = new Param[String](this, "inputCol", "input column name")
  final def getInputCol: String = $(inputCol)

  final val outputCol: Param[String] = new Param[String](this, "outputCol", "output column name")
  setDefault(outputCol, uid + "__output")
  final def getOutputCol: String = $(outputCol)

  final val thenSetValue: Param[String] = new Param[String](this, "string constant", "a string constant")
  final def getThenSetValue: String = $(thenSetValue)

  final val elseSetCol: Param[String] = new Param[String](this, "column", "a column name that exists in the dataframe")
  final def getElseSetCol: String = $(elseSetCol)

  /** Validates and transforms the input schema. */
  protected def validateAndTransformSchema(schema: StructType): StructType = {
    //val typeCandidates = List(new ArrayType(StringType, true), new ArrayType(StringType, false))
    //CustomSchemaUtil.checkColumnTypes(schema, $(inputCol), typeCandidates)
    CustomSchemaUtil.appendColumn(schema, $(outputCol), new VectorUDT)
  }
}

class IfZeroVector(override val uid: String)
  extends Transformer with IfZeroVectorParams {

  def this() {
    this(Identifiable.randomUID("IfZeroVectorTransformer"))
  }

  def setInputCol(value: String): this.type = set(inputCol, value)

  def setOutputCol(value: String): this.type = set(outputCol, value)

  def setThenSetValue(value: String): this.type = set(thenSetValue, value)

  def setElseSetCol(value: String): this.type = set(elseSetCol, value)

  override def transform(dataFrame: DataFrame): DataFrame = {
    transformSchema(dataFrame.schema)

    val encode = udf { (inputVector: Vector, toSetCol:String) =>
      inputVector match {
        case dv:DenseVector =>
          if(dv.numNonzeros == 0) getThenSetValue else toSetCol
        case sv:SparseVector =>
          if(sv.numNonzeros == 0) getThenSetValue else toSetCol
        case v => throw new IllegalArgumentException("Do not support vector type " + v.getClass)
      }
    }
    dataFrame.withColumn($(outputCol), encode(col($(inputCol)), col($(elseSetCol))))
  }

  override def transformSchema(schema: StructType): StructType = {
    val inputType = schema($(inputCol)).dataType
    require(inputType.isInstanceOf[VectorUDT],
      s"Input column ${$(inputCol)} must be a vector column")
    require(!schema.fieldNames.contains($(outputCol)),
      s"Output column ${$(outputCol)} already exists.")
    return CustomSchemaUtil.appendColumn(schema, $(outputCol), new VectorUDT)
  }

  override def copy(extra: ParamMap): IfZeroVector = {
    val copied = new IfZeroVector(uid)
    copyValues(copied, extra)
  }
}

