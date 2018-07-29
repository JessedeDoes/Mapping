package sparql2xquery

import sparql2xquery.Mappings.PredicateMapping
import sparql2xquery.SparqlToXquery.parseResult

object TEIMapping {
  type PredicateMapping = Map[String, BasicPattern]
  val ontolex = "http://www.w3.org/ns/lemon/ontolex#"
  val  skos = "http://www.w3.org/2004/02/skos/core#"

  val testMap:PredicateMapping = Map(
    s"${ontolex}lexicalForm" ->
      BasicPattern(
        Set("subject←//entry",
          "object←$subject//oVar"),
        Set("subject", "object")
      ),

    s"${ontolex}id" ->
      BasicPattern(
        Set("object←$subject/@id"),
        Set("subject", "object")
      ),


    s"${ontolex}resource" ->
      BasicPattern(
        Set("object←concat('http://rdf.ivdnt.org/wnt/genid/',$subject/generate-id())"),
        Set("subject", "object")
      ),

    s"${ontolex}canonicalForm" ->
      BasicPattern(
        Set(
          "object←$subject[self::entry]//form[@type='lemma' and not (ancestor::re)]|$subject[self::re]//form[@type='lemma']"),
       Set("subject", "object")),

    s"${ontolex}attestation" ->
      BasicPattern(
        Set(
          "object←$subject/ancestor::entry//oVar[./text()=$subject/orth/text()"), // klopt niet helemaal
        Set("subject", "object")),

    s"${ontolex}textBefore" -> // dit komt niet door xql heen, maar mag wel in basex ....
      BasicPattern(
        Set(
          "subject←//oVar",
          "q0←($subject/ancestor::q)[1]",
          //"object←$q0//text()[1]/index-of($q0/text(),.)"
          "object←string-join($q0//text()[index-of($q0//node(),.)[1] < index-of($q0//node(),($subject//text())[1])[1]],'')"
        ),
        Set("q0", "subject", "object")),

    s"${ontolex}sense" ->
      BasicPattern(
        Set(
          "object←$subject[self::entry]//sense"),
        Set("subject", "object")),

    s"${ontolex}citation" ->
      BasicPattern(
        Set("object←$subject/eg/cit"),
        Set("subject", "object")),

    s"${skos}definition" ->
      BasicPattern(
        Set("object←$subject[self::sense]/def/text()"),
        Set("subject", "object")),

    s"${ontolex}writtenRep" ->
      BasicPattern(
        Set("object←$subject[self::form[@type='lemma']]/orth/text()|$subject[self::oVar]/text()"),
          Set("subject", "object")
      ),
  )

  val teiMapping = TripleMapping(testMap)

  val q0 =
    s"""
       |prefix ontolex: <$ontolex>
       | select ?e0 ?l ?w where
       | {?e0 ontolex:canonicalForm ?cf .
       |   ?cf ontolex:writtenRep ?l .
       |   ?e0 ontolex:lexicalForm ?lf .
       |   ?lf ontolex:writtenRep ?w }
     """.stripMargin

  val q1 =
    s"""
       |prefix ontolex: <$ontolex>
       |prefix skos: <$skos>
       |select ?i0 ?f0 ?w ?l ?r  where
       |{
       |  ?e0 ontolex:lexicalForm ?f0 .
       |  ?e0 ontolex:id ?i0 .
       |  ?e0 ontolex:canonicalForm ?c0 .
       |  ?c0 ontolex:writtenRep ?l .
       |  ?f0 ontolex:writtenRep ?w .
       |  ?f0 ontolex:resource ?r .
       |}
     """.stripMargin

  val q2 =
    s"""
       |prefix ontolex: <$ontolex>
       |select ?v0 ?t where
       |{
       |  ?v0 ontolex:textBefore ?t .
       |}
     """.stripMargin

  val q3 =
    s"""
       |prefix ontolex: <$ontolex>
       |prefix skos: <$skos>
       |select ?i0  ?l  ?d where
       |{
       |  ?e0 ontolex:sense ?s0 .
       |  ?e0 ontolex:id ?i0 .
       |  ?e0 ontolex:canonicalForm ?c0 .
       |  ?c0 ontolex:writtenRep ?l .
       |  ?s0 skos:definition ?d
       |}
     """.stripMargin

  def main(args: Array[String]): Unit = {
    val t = new SparqlToXquery(teiMapping)
    val x = t.translate(q3)
    Console.err.println(x.toQuery())
    val bx = basex.BaseXConnection.wnt
    val resultStream = bx.getAsScalaNodes(x.toQuery()).map(parseResult)
    println(QueryResults.response(resultStream.take(1000)))
  }
}
