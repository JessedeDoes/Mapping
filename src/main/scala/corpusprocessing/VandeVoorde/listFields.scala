package corpusprocessing.VandeVoorde

object listFields {
   def main(args: Array[String])  = {
     val x = corpusprocessing.corpusutils.listIndexedFields("http://svatkc10.ivdnt.loc/blacklab-server/CorpusVandevoorde/")
     x.main(args)
   }
}