package com.calabs.dss.datasave

import scopt.OptionParser

/**
 * Created by Jordi Aranda
 * <jordi.aranda@bsc.es>
 * 9/1/15
 */

object DSSDataSave {

  case class Config(db: String, properties: String)

  def main(args: Array[String]) : Unit = {

    val parser = new OptionParser[Config]("dss-data-save") {
      head("DSS Data Save tool", "0.0.1")
      opt[String]('d', "database") required() action{ (x,c) =>
        c.copy(db = x)} text("Database backend (current supported implementations are 'neo4j', 'titan' and 'arangodb')")
      opt[String]('p', "properties") required() action{ (x,c) =>
        c.copy(properties = x)} text("Blueprints and concrete vendor properties for underlying chosen database storage")
    }

    parser.parse(args, Config("database", "properties")) map {
      config => {
        // TODO: Read JSON from stdin, parse metrics and store them into selected db
        for(ln <- io.Source.stdin.getLines) println(ln)
      }
    }

  }

}
