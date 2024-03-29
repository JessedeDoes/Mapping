package db2rdf

import java.io.FileInputStream
import java.sql.ResultSet

import org.postgresql.ds.PGPoolingDataSource
import org.skife.jdbi.v2.tweak.ResultSetMapper
import org.skife.jdbi.v2.{DBI, Handle, Query, StatementContext}

import scala.collection.JavaConverters._
import org.postgresql.core.BaseConnection
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource
import com.mysql.jdbc.Driver
import database.DatabaseUtilities.AlmostQuery
import database.DatabaseUtilities.makeMapping
import database.DatabaseUtilities.ResultMapping
import database.Configuration
import net.sf.jsqlparser.schema.Database
import org.openrdf.rio.RDFFormat
import org.openrdf.rio.RDFParser
import org.openrdf.rio.Rio
import rdf.Schema

import scala.util.{Failure, Success, Try}

trait Literal
trait Statement
{

  def isQuad:Boolean = ???
  def toString1:String = ???
  def withGraph(i: IRI): Statement = ???

  def valid():Boolean = // http://archive.rdf4j.org/users/ch09.html
  {
    val in = new java.io.StringReader(toString1)
    val p = Rio.createParser(if (isQuad) RDFFormat.NQUADS else RDFFormat.NTRIPLES)
    //Console.err.println(s"$toString1 $isQuad")
    Try (p.parse(in, "http://bullshit")) match {
      case Failure(e) =>  System.err.println(s"problem with $toString1!!"); false
      case Success(x) => true
    }
  }
}

object Sort extends Enumeration {
  type Sort = Value
  val ClassType, ObjectPropertyType, DataPropertyType, Noop = Value
}
import Sort._

case class UndefinedLiteral(s: String, lang: String="nl") extends Literal

case class StringLiteral(s: String, lang: Option[String]=Some("nl")) extends Literal
{
  lazy val escaped: String = Try(s.replaceAll("\"", "\\\\\"").replaceAll("\\s+", " ").trim) match {
    case Success(x) => x
    case Failure(e) => {
      e.printStackTrace()
      "DIKKE_EXCEPTION"
    }
  }
  override def toString = if (Settings.useLangStrings && lang.isDefined) s""""$escaped"@$lang""" else s""""$escaped""""
}

case class IntLiteral(k: Int) extends Literal  { override def toString = s""""$k"^^<http://www.w3.org/2001/XMLSchema#int>""";   }
case class BooleanLiteral(k: Boolean) extends Literal  { override def toString = s""""$k"^^<http://www.w3.org/2001/XMLSchema#boolean>""";   }

case class IRI(s: String, implicit val schema: Schema=null)(implicit val sort:Sort = Noop)
{
  def validate():Boolean = if (schema != null && sort != Noop)
    {
      val valid = sort match {
        case ClassType => schema.classNames.contains(s)
        case ObjectPropertyType => schema.objectPropertyNames.contains(s)
        case DataPropertyType => schema.dataPropertyNames.contains(s)
      }
      if (!valid) Console.err.println(s"Unknown $sort: $s!!!")
      valid
    } else true

  validate()
  override def toString = '<' + s.toString + '>'
}

case class ObjectProperty(s: IRI, p: IRI, o: IRI, g:Option[IRI]=None) extends Statement
{
  override lazy val toString1 = s"$s $p $o ${if (g.isDefined) g.get.toString else ""} ."
  override def isQuad: Boolean = g.isDefined
  override def toString = if (valid()) toString1 else "<an> <error> <occured>"

  override def withGraph(i: IRI): Statement = this.copy(g = Some(i))
}

case class NoStatement() extends Statement {
  override def valid(): Boolean = false
}

case class DataProperty(s: IRI, p: IRI, o: Literal, g:Option[IRI]=None) extends Statement
{
  override def toString1 = s"$s $p $o ${if (g.isDefined) g.get.toString else ""} ."
  override def isQuad: Boolean = g.isDefined
  override def toString = if (valid()) toString1 else "<an> <error> <occured>"
  override def withGraph(i: IRI): Statement = this.copy(g = Some(i))
  def objectIsDefined: Boolean = !o.isInstanceOf[UndefinedLiteral]
}

trait Mapping

case class NopeMapping() extends Mapping {
  def triples(db: database.Database, q: String) : Stream[ObjectProperty] = Stream.empty
}

case class Ω(p: ResultSet => IRI, s: ResultSet => IRI, o: ResultSet => IRI) extends Mapping
{
  def triples(db: database.Database, q: String) : Stream[ObjectProperty] =
    {
      val query: AlmostQuery[ObjectProperty] = db => db.createQuery(q).map(ResultMapping(r => ObjectProperty(s(r) ,p(r), o(r))))
      db.stream(query)
    }
}

case class Δ(p: IRI, s: ResultSet => IRI, o: ResultSet => Literal) extends Mapping
{
  def triples(db: database.Database, q: String) : Stream[DataProperty] =
    {
      val query: AlmostQuery[DataProperty] = db => db.createQuery(q).map(ResultMapping(r => DataProperty(s(r) ,p, o(r))))
      db.stream(query).filter(dp => !dp.o.isInstanceOf[UndefinedLiteral]) // beperkt dit tot gevallen waar het object gedefineerd is....
    }
}

case class Mappings(query: String, mappings:Seq[Mapping])
{
  def multiMapping(mappings:Seq[Mapping]): ResultMapping[Set[Statement]] =
  {
    val dps: Set[Δ] = mappings.filter(_.isInstanceOf[Δ]).map(_.asInstanceOf[Δ]).toSet
    val ops: Set[Ω] = mappings.filter(_.isInstanceOf[Ω]).map(_.asInstanceOf[Ω]).toSet
    ResultMapping(r => ops.map(x => ObjectProperty(x.s(r) ,x.p(r), x.o(r))) ++
      dps.map(x => DataProperty(x.s(r) ,x.p, x.o(r))).filter(_.objectIsDefined)  )
  }

  def triplesStream(db: database.Database, q: String): Stream[Statement] =
  {
    val m = multiMapping(this.mappings)
    val query : AlmostQuery[Set[Statement]] = db => db.createQuery(q).map(m)
    db.stream(query).flatten.filter(_.valid()) // ok it is the flatten that hurts ....
  }

  def triplesIterator(db: database.Database, q: String): Iterator[Statement] =
  {
    val m = multiMapping(this.mappings)
    val query : AlmostQuery[Set[Statement]] = db => db.createQuery(q).map(m)
    db.iterator(query).flatten.filter(_.valid())
  }

  def triplesIterator(db: database.Database): Iterator[Statement] = triplesIterator(db, this.query)
}



object Ω {
  implicit def x(s: String): ResultSet => IRI = r => IRI(r.getString(s))

  case class ϝ(field: String, f: String => String = identity)

  implicit def fd(x: (String, String => String)):ϝ = ϝ(x._1,x._2)

  implicit def y(d: ϝ): ResultSet => IRI = r => IRI(d.f(r.getString(d.field)))
  implicit def z(d: ϝ): ResultSet => StringLiteral = r => StringLiteral(d.f(r.getString(d.field)))
  implicit def z(x: (String, String => String)): ResultSet => StringLiteral = { val d:ϝ = x; r => StringLiteral(d.f(r.getString(d.field))) }
  implicit def i(s: String):IRI = IRI(s)
  implicit def toFunction(i: IRI): ResultSet => IRI = r => i

  case class XXX(s: String)
  {
    import scala.util.matching.Regex
    val f:ResultSet => Literal = r =>  {
      val x = r.getString(s)
      if (x == null || x.trim.isEmpty) UndefinedLiteral("UNDEFINED") else
      StringLiteral(r.getString(s))
    }

    lazy val asTemplate: ResultSet => IRI  =
    {
      val varNames = "\\$([a-zA-Z][a-zA-Z0-9_]+)".r.findAllMatchIn(s).toStream.map(_.group(1))
      val z = if (varNames.nonEmpty)
        {
          val g:ResultSet => IRI = r =>
          {
            // Console.err.println(varNames.toList)
            //Console.err.println((1 to r.getMetaData.getColumnCount).toList.map(i =>  r.getMetaData.getColumnName(i)))
            val substituted = varNames.foldLeft(s)( (z, v) =>  {
              val value = r.getString(v)
              val v1 = if (value== null) "null" else value
              z.replaceAll("\\$" + v, v1)
            })
            val i = IRI(substituted)
            i
          }
          g
        } else
        {
          val g:ResultSet => IRI  = r => IRI("bla")
          g
        }
      z
    }

    def unary_~ = asTemplate
    def unary_! =f
  }

  implicit def xxx(s:String):XXX = XXX(s)

  def ⊕(q: String, l: Mapping*) = Mappings(q, l)

  ////////////////////////////////////////////////////////

  def main(args: Array[String])=
  println(DataProperty("http://aap", "http://noot", IntLiteral(3)))


}
