package lila.i18n

import java.io._
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import play.api.libs.json.{ JsObject, JsString }
import play.api.i18n.Lang

final private[i18n] class JsDump(path: String)(implicit ec: scala.concurrent.ExecutionContext) {

  def apply: Funit =
    Future {
      pathFile.mkdir
      writeRefs
      writeFullJson
    } void

  private val pathFile = new File(path)

  private def dumpFromKey(keys: Set[String], lang: Lang): String =
    keys
      .map { key =>
        """"%s":"%s"""".format(key, escape(Translator.txt.literal(key, I18nDb.Site, Nil, lang)))
      }
      .mkString("{", ",", "}")

  private def writeRefs() = writeFile(
    new File("%s/refs.json".format(pathFile.getCanonicalPath)),
    LangList.all.toList
      .sortBy(_._1.code)
      .map {
        case (lang, name) => s"""["${lang.code}","$name"]"""
      }
      .mkString("[", ",", "]")
  )

  private def writeFullJson() = I18nDb.langs foreach { lang =>
    val code = dumpFromKey(I18nDb.site(defaultLang).keySet.asScala.toSet, lang)
    writeFile(new File("%s/%s.all.json".format(pathFile.getCanonicalPath, lang.code)), code)
  }

  private def writeFile(file: File, content: String) = {
    val out = new PrintWriter(file)
    try {
      out.print(content)
    } finally {
      out.close
    }
  }

  private def escape(text: String) = text.replaceIf('"', "\\\"")
}

object JsDump {

  private def quantitySuffix(q: I18nQuantity): String = q match {
    case I18nQuantity.Zero  => ":zero"
    case I18nQuantity.One   => ":one"
    case I18nQuantity.Two   => ":two"
    case I18nQuantity.Few   => ":few"
    case I18nQuantity.Many  => ":many"
    case I18nQuantity.Other => ""
  }

  private type JsTrans = Iterable[(String, JsString)]

  private def translatedJs(k: String, t: Translation): JsTrans = t match {
    case literal: Simple  => List(k -> JsString(literal.message))
    case literal: Escaped => List(k -> JsString(literal.message))
    case plurals: Plurals =>
      plurals.messages.map {
        case (quantity, msg) => s"$k${quantitySuffix(quantity)}" -> JsString(msg)
      }
  }

  def keysToObject(keys: Seq[I18nKey], lang: Lang): JsObject = JsObject {
    keys.flatMap { k =>
      Translator.findTranslation(k.key, k.db, lang).fold[JsTrans](Nil) { translatedJs(k.key, _) }
    }
  }

  val emptyMessages: MessageMap = new java.util.HashMap()

  def dbToObject(ref: I18nDb.Ref, lang: Lang): JsObject =
    I18nDb(ref).get(defaultLang) ?? { defaultMsgs =>
      JsObject {
        val msgs = I18nDb(ref).get(lang) | emptyMessages
        defaultMsgs.asScala.flatMap {
          case (k, v) => translatedJs(k, msgs.getOrDefault(k, v))
        }
      }
    }
}
