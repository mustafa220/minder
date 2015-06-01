package global

import java.lang.reflect.Field
import java.util._

import controllers.Assets
import editormodels.AssertionEditorModel
import models.{User, PrescriptionLevel}
import play.Logger
import play.data.Form
import play.data.validation.{Constraints, ValidationError}

/**
 * Created by yerlibilgin on 02/05/15.
 */
object Util {
  def choose(value: Any, expected: Any, matchValue: String = "activetab", nonMatchValue: String = "passivetab"): String = {
    if (value == expected)
      matchValue
    else
      nonMatchValue
  }

  /*
   * test case CRUD
   */
  def printFormErrors(filledForm: Form[_]) {
    val errors: Map[String, List[ValidationError]] = filledForm.errors
    val set: Set[String] = errors.keySet
    import scala.collection.JavaConversions._
    for (key <- set) {
      Logger.error("KEY")
      import scala.collection.JavaConversions._
      for (ve <- errors.get(key)) {
        Logger.error("\t" + ve.key + ": " + ve.message)
      }
    }
  }


  def checkField(cls: Class[_], fieldName: String, newValue: String): Unit ={
    System.out.println("Class Name"+cls);
    System.out.println("Field Name"+fieldName);
    val fld: Field = cls.getDeclaredField(fieldName)
    val required: Boolean = fld.getAnnotation(classOf[Constraints.Required]) != null

    var minLength: Long = 0
    var maxLength: Long = 0

    val minLenAnnotation: Constraints.MinLength = fld.getAnnotation(classOf[Constraints.MinLength])

    if (minLenAnnotation != null) {
      minLength = minLenAnnotation.value
    }

    val maxLenAnnotation: Constraints.MaxLength = fld.getAnnotation(classOf[Constraints.MaxLength])

    if (maxLenAnnotation != null) {
      maxLength = maxLenAnnotation.value
    }


    println(required + " " + minLength + " " + maxLength + " [" + newValue + "]")

    if (required && (newValue == null || newValue.isEmpty)) {
      throw new IllegalArgumentException(fieldName + " is required. Cannot be empty!.")
    }


    if (minLength > 0 && (newValue == null || newValue.length < minLength)) {
      throw new IllegalArgumentException(fieldName + " length cannot be less than " + minLength)
    }


    if (maxLength > 0 && (newValue == null || newValue.length > maxLength)) {
      throw new IllegalArgumentException(fieldName + " length cannot be more than " + maxLength)
    }
  }

  abstract class Converter{
    def convert(any: Any) : Any;
  }

  case class PrescriptionLevelConverter() extends Converter{
    override def convert(any: Any) : Any ={
      PrescriptionLevel.valueOf(any.toString);
    }
  }

  val converters = scala.collection.immutable.Map("PrescriptionLevel" -> PrescriptionLevelConverter());

  def convertValue(converter: String, newValue: String) : Any = {
    converters(converter).convert(newValue)
  }


  def getBooleanIcon(flag: Boolean) : String = {
    if (flag) {"/images/Happy-32.png"} else {"/images/Sad-32.png"}
  }


  def canAccess(localUser: User, owner: User) : Boolean = {
    println(localUser.email + " vs. " + owner.email)
    localUser.email=="root@minder" || localUser.email==owner.email
  }
}
