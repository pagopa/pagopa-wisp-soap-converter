package it.gov.pagopa.common.repo

import com.fasterxml.jackson.annotation.JsonProperty

case class CosmosPrimitive(partitionKey:String,id:String,primitive:String,header:String,body:String) {

  def getId(): String = id
  @JsonProperty(value="PartitionKey")
  def getPartitionKey(): String = partitionKey
  def getPrimitive(): String = primitive
  def getHeader(): String = header
  def getBody(): String = body

}
