package corpusprocessing.edges.openEdges

import corpusprocessing.edges.openEdges.Alignment.readCorrespondences

import java.io.{File, PrintWriter}
import scala.xml.PrettyPrinter
import scala.xml._
import utils.PostProcessXML
import corpusprocessing.clariah_training_corpora.fixTokenization.getId

case class BibleCorpus(baseDir: String, alignment_files: Set[String]) {

  lazy val allAlignments = SetOfAlignments(this, alignment_files)


  lazy val bibles: List[Bible] = {
    allAlignments.bibles.map{case (l,b) => Bible(this,l,b)}
  }.toList.filter(!_.isEmpty)

  lazy val allBookNames = bibles.flatMap(_.bookNames).toSet.toList.sorted

  def setAttribute(e: Elem, prefix: String, name: String, value: String): Elem = {
    val a = e.attributes.filter(_.key != name).append(new PrefixedAttribute(prefix, name, value, Null))
    e.copy(attributes = a)
  }

  def setAttribute(e: Elem, name: String, value: String): Elem = {
    val a = e.attributes.filter(_.key != name).append(new UnprefixedAttribute(name, value, Null))
    e.copy(attributes = a)
  }

  def makeIdsGloballyUnique(d: Elem): Elem = {
    PostProcessXML.updateElement(d, _.label == "ab", x => PostProcessXML.updateElement(x, e => Set("w","pc").contains(e.label), y => setAttribute(y, "xml", "id", getId(x) + "." + getId(y))))
  }

  def includeBooks(d: Elem, fileName: String) = {
    PostProcessXML.updateElement(d, _.label == "include", x => {
      val href = (x \ "@href").text.replaceAll("content", "tokenized")
      val dir = new File(fileName).getParentFile.getCanonicalPath
      val included = XML.load(dir + "/" + href)
      setAttribute(x.copy(child = included), "href", href)
    })
  }

  def removeBookIncludes(d: Elem)  = {
    PostProcessXML.updateElement(d, _.label == "include", x => {
      x.copy(child = Seq())
    })
  }

  def addWordAlignment(bookname: String, fileName: String) = {

    val alignments = allAlignments.alignments.map(a => {
      Alignment(a.correspondences.filter(_.book == bookname))
    }).filter(_.nonEmpty)

    alignments.foreach(a => println(a.correspondences.size))

    val bookXML = XML.load(fileName)

    val  dBooksIncluded = includeBooks(bookXML, fileName)

    val dUniqueIds = makeIdsGloballyUnique(dBooksIncluded)

    val processed = alignments.foldLeft(dUniqueIds)({case (e,a) => align.fastAlign.addWordAlignment(e, a)})
    XML.save(fileName.replaceAll(".xml$", ".wordAligned.xml"), removeBookIncludes(processed))
  }

  def printBooks(toDir: String = "/tmp/Bible/") = {
    new File(toDir).mkdir()
    val subdir = toDir + "/content/"
    new File(subdir).mkdir()

    bibles.foreach(b => {
      val books = b.books
      books.foreach(bk => {
        val f = subdir + bk.xmlFileName
        bk.printTo(f)
      })
    })
  }

  def printBookAlignments(toDir: String = "/tmp/Bible/"): Unit = {
    Console.err.println(s"Collecting alignments for books: $allBookNames")
    new File(toDir).mkdir()
    allBookNames.foreach(bkname => {

      Console.err.println(s"Collecting alignments for book: $bkname")

      new File(toDir).mkdir()
      val books = bibles.map(_.getBook(bkname)).filter(_.nonEmpty).map(_.get)
      val includes = books.map(b => <xi:include href={"../content/" + b.xmlFileName}/>)
      val allCorrespondences = allAlignments.alignments.map(a => {
        val id = a.bible1 + "--" + a.bible2
        val correspondences = a.filterByBook(bkname)
        <standOff type="book-alignment" xml:id={id}>{correspondences.map(_.toXML)}</standOff>
      })

      val xml = <teiCorpus xmlns:xi="http://www.w3.org/2001/XInclude" xmlns="http://www.tei-c.org/ns/1.0">
        <teiHeader>
          <fileDesc>
            <titleStmt>
              <title>Link file for {bkname}</title>
            </titleStmt>
          </fileDesc>
        </teiHeader>
        {includes}
        {allCorrespondences}
      </teiCorpus>
      val subdir = toDir + "/alignments/"
      new File(subdir).mkdir()
      val fileName = subdir + bkname + ".xml"
            // println(formatted)
      val pw = new java.io.PrintWriter(fileName)
      //pw.println(formatted)
      prettyPrinting.prettyScala(pw, xml)
      pw.close()
    })
  }
}