object JsonExample extends App {
  import org.json4s._
  import org.json4s.JsonDSL._
  import org.json4s.jackson.JsonMethods._

  case class Winner(id: Long, numbers: List[Int])
  case class Lotto(id: Long, winningNumbers: List[Int], winners: List[Winner], drawDate: Option[java.util.Date])

  val winners = List(Winner(23, List(2, 45, 34, 23, 3, 5)), Winner(54, List(52, 3, 12, 11, 18, 22)))
  val lotto = Lotto(5, List(2, 45, 34, 23, 7, 5, 3), winners, None)

  val bla: List[(String,JsonAST.JValue)] = List("aap" -> "noot", "wim" -> "zus")

  def foldie(a: JsonAST.JObject, b: (String, String)) = a ~ b

  val start = new JsonAST.JObject(List())

  //val gnagna = bla.reduce({case (a: ((String,String)),b: ((String,String))) => {val x = a ~ b; x} })

  val json =
    ("lotto" ->
      ("lotto-id" -> lotto.id) ~
        ("stuff" -> bla) ~
      ("winning-numbers" -> lotto.winningNumbers) ~
      ("draw-date" -> lotto.drawDate.map(_.toString)) ~
      ("winners" ->
        lotto.winners.map { w =>
          (("winner-id" -> w.id) ~
           ("numbers" -> w.numbers))}))


  println(compact(render(json)))
}