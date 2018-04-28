package db2rdf

import org.semanticweb.owlapi.model.OWLClass
import propositional._

import scala.collection.JavaConverters._

class Schema(fileName: String) {

  import org.semanticweb.owlapi.apibinding.OWLManager
  import org.semanticweb.owlapi.model.OWLOntology
  import org.semanticweb.owlapi.model.OWLOntologyManager

  val manager: OWLOntologyManager = OWLManager.createOWLOntologyManager
  val ontology: OWLOntology = manager.loadOntologyFromOntologyDocument(new java.io.File(fileName))

  val classes:Set[OWLClass] = ontology.getClassesInSignature().asScala.toSet
  val objectProperties = ontology.getObjectPropertiesInSignature().asScala.toSet
  val dataProperties = ontology.getDataPropertiesInSignature().asScala.toSet
  val axioms = ontology.getTBoxAxioms(null).asScala.toSet

  //axioms.foreach(println)

  val objectPropertyNames = objectProperties.map(op => op.getIRI.toString)
  val dataPropertyNames = dataProperties.map(op => op.getIRI.toString)
  val classNames = classes.map(op => op.getIRI.toString)

  def validObjectProperty(s: String):Boolean = objectPropertyNames.contains(s)
  def validDataProperty(s: String):Boolean = dataPropertyNames.contains(s)
  def validClass(s: String):Boolean = classNames.contains(s)

  def prop2owl(p: Proposition): String =
  {
    p match {
      case Literal(a) => a
      case Not(a) => s"ObjectComplementOf(${prop2owl(a)})"
      case And(a, b) => s"ObjectIntersectionOf((${prop2owl(a)}) (${prop2owl(b)}) )"
      case Or(a, b) => s"ObjectUnionOf(${prop2owl(a)} ${prop2owl(b)} )"
      case Implies(a, b) => s"SubclassOf((${prop2owl(a)}) (${prop2owl(b)}) )"
      case Equiv(a, b) => s"EquivalentClasses(${prop2owl(a)} ${prop2owl(b)} )"
    }
  }
}

object testSchema
{
  val s = new Schema("data/Diamant/diamant.fss")

  def main(args: Array[String]): Unit =
  {
    println("#classes:")
    s.classNames.toList.sortBy(identity).foreach(println)
    println("#object properties")
    s.objectPropertyNames.toList.sortBy(identity).foreach(println)
    println("#data properties")
    s.dataPropertyNames.toList.sortBy(identity).foreach(println)
    println("#axioms")
    s.axioms.foreach(a => println(s"$a ${a.getAxiomType}"))
  }
}
