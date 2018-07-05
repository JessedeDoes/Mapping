package posmapping

import CRM._


import scala.xml._

object wordSplitting {

  val squareCup = "⊔"

  def splitW(w: Elem): NodeSeq = // lelijk: we verliezen de tagging van expan enzo....
  {
    val word = (w \ "seg").text.trim
    val pos = (w \ "@msd").text

    val parts = word.split(s"(\\s+|$squareCup)")
    
    if (parts.size == 1)
      {
        Seq(w)
      } else {
      parts.toSeq.zipWithIndex.map({ case (p,i) =>

          val newSeg = <seg>{p}</seg>


          val partClass = if (i == 0) "deel-b" else if (i == parts.size - 1) "deel-f" else "deel-i"

          val newPos = pos.replaceAll("\\)$", s",$partClass)").replaceAll("\\(,", "(")

          val newAtts = w.attributes.filter(x => !(x.key == "msd")).append(  new UnprefixedAttribute("msd",newPos,Null) )

          val newChild = newSeg ++ w.child.filter(c => !(c.label == "seg"))

          val pw =w.copy(child=newChild, attributes = newAtts)

          val fs = (w \ "fs").toSeq
          val fsNew = if (fs.nonEmpty)
            {
              val hd = fs.head.asInstanceOf[Elem]
              Seq(hd.copy(child = hd.child ++ Seq(<f name="deel">{partClass}</f>))) ++ fs.tail }
          else
            Seq()

            // Console.err.println(pw)
          pw.copy(child = pw.child.filter(c => !(c.label == "fs")) ++ fsNew)
        }
      )
    }
  }

  import utils.PostProcessXML._

  def splitWords(d: Elem):Elem = updateElement2(d, _.label=="w", splitW).asInstanceOf[Elem]


}
