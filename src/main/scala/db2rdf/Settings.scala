package db2rdf

import database.Configuration

object Settings {
  val gigantHilexDB = new database.Database(Configuration("x", "svowdb06","gigant_hilex_candidate", "fannee", "Cric0topus"))
  val doLexCit = false
  val useLangStrings = false
  val outputDefinitionsAndQuotations = true
  val outputFolderForDiamantRDF =  "/data/Diamant/RDF" // "/mnt/Projecten/CLARIAH/Scratch"
}
