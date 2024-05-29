package corpusprocessing.clariah_training_corpora.training_data_extraction.cobaltje

import java.io.{File, PrintWriter}
import Settings._
import corpusprocessing.clariah_training_corpora.training_data_extraction.{TrainingDataInfo, TrainingDataInfos}

object ExtractionFromCobaltTrainingCorpora {

  def main(args: Array[String]) = {

    val extractedSets = new File(directoryWithCobaltExports).listFiles()
      .filter(_.getName.endsWith(".zip"))
      //.filter(_.getName.contains("gtbcit_mnw_15"))
      .map(f => {
        val datasetName = f.getName().replaceAll("^cobalt_export_", "").replaceAll(".zip", "")
        val dirAsDir = new File(createdTrainingDataDirectory + "/" + datasetName)
        // dirAsDir.delete()
        dirAsDir.mkdir()
        val outputPrefix = createdTrainingDataDirectory + "/" + datasetName + "/" + datasetName

        val e = ExtractionFromCobaltExport(f.getCanonicalPath, outputPrefix,
          sentenceElement = if (datasetName.contains("cit")) "q" else "s")
        datasetName -> e.extract()
      }).toMap

      val infos = TrainingDataInfos(directoryWithCobaltExports, createdTrainingDataDirectory, extractedSets)
      val w = new PrintWriter(createdTrainingDataDirectory + "/"  + "cobaltSets.json")
      w.println(TrainingDataInfos.write(infos))
      w.close()
  }
}

object ExtractionFromCobaltTrainingCorporaWithConfig {

  val jsonLocation ="/mnt/Projecten/Corpora/TrainingDataForTools/CobaltExport/2024_2/training-data-2/cobaltSets.json"
  def main(args: Array[String]) = {
    val info = TrainingDataInfos.readFromFile(jsonLocation)
    val extractTo = info.extractedDataDir.replaceAll("/$", "") + ".test_reextract"
    new File(extractTo).mkdir()
    new File(info.downloadedDataDir).listFiles()
      .filter(_.getName.endsWith(".zip"))
      //.filter(_.getName.contains("gtbcit_mnw_15"))
      .foreach(f => {
        val datasetName = f.getName().replaceAll("^cobalt_export_", "").replaceAll(".zip", "")
        val datasetConfig = info.trainingDataInfos(datasetName)
        val dirAsDir = new File(extractTo + "/" + datasetName)
        // dirAsDir.delete()
        dirAsDir.mkdir()
        val outputPrefix = extractTo + "/" + datasetName + "/" + datasetName

        val e = ExtractionFromCobaltExport(f.getCanonicalPath, outputPrefix,
          sentenceElement = datasetConfig.sentenceElement, ///if (datasetName.contains("cit")) "q" else "s",
          enhanceTags = false, // dan wordt dus alles wel anders.......
          info=Some(datasetConfig)
        )
        val newConfig = e.extract()
        if (newConfig != datasetConfig) {
          Console.err.println(s"Hm, het is niet hetzelfde voor $datasetName")
        }
      })
  }
}

object doBoth { // dit werkt niet vanwege
  def main(args: Array[String]) = {
    ExtractionFromCobaltTrainingCorpora.main(Array())
    ExtractionFromCobaltTrainingCorporaWithConfig.main(Array())
  }
}

/*
Failure opening /mnt/Projecten/Corpora/TrainingDataForTools/CobaltExport/2024_2/download/cobalt_export_wnt_citaten_19.zip
java.nio.file.FileSystemAlreadyExistsException
  at com.sun.nio.zipfs.ZipFileSystemProvider.newFileSystem(ZipFileSystemProvider.java:113)
  at java.nio.file.FileSystems.newFileSystem(FileSystems.java:326)
  at java.nio.file.FileSystems.newFileSystem(FileSystems.java:276)
  at utils.zipUtils$.getRootPath(zip.scala:23)
  at utils.zipUtils$.find(zip.scala:29)
 */
