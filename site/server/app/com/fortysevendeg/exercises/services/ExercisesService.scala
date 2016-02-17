/*
 * scala-exercises-server
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package com.fortysevendeg.exercises.services

import com.fortysevendeg.exercises.Exercises
import com.fortysevendeg.exercises.MethodEval

import play.api.Logger

import scala.reflect.runtime.{ universe ⇒ ru }
import scala.reflect.runtime.{ currentMirror ⇒ cm }
import scala.tools.reflect.ToolBox

import cats.data.Xor
import cats.data.Ior
import cats.std.option._
import cats.syntax.flatMap._

object ExercisesService extends RuntimeSharedConversions {

  lazy val methodEval = new MethodEval()

  val (errors, runtimeLibraries) = Exercises.discoverLibraries(cl = ExercisesService.getClass.getClassLoader)
  val (libraries, librarySections) = {
    val libraries1 = colorize(runtimeLibraries)
    errors.foreach(error ⇒ Logger.warn(s"$error")) // TODO: handle errors better?
    (
      libraries1.map(convertLibrary),
      libraries1.map(library0 ⇒ library0.name → library0.sections.map(convertSection)).toMap
    )
  }

  def section(libraryName: String, name: String): Option[shared.Section] =
    librarySections.get(libraryName) >>= (_.find(_.name == name))

  def evaluate(evaluation: shared.ExerciseEvaluation): Xor[Throwable, Unit] = {
    val res = methodEval.eval(
      evaluation.method,
      evaluation.args
    )
    Logger.info(s"evaluation for $evaluation: $res")
    res.fold(_ match {
      case Ior.Left(message)        ⇒ Xor.left(new Exception(message))
      case Ior.Right(error)         ⇒ Xor.left(error)
      case Ior.Both(message, error) ⇒ Xor.left(new Exception(message, error))
    }, _ ⇒ Xor.right(Unit))
    // uncomment this next line if you want to actually throw the exception
    //.bimap(e ⇒ { throw e; e }, _ ⇒ Unit)
  }

}

sealed trait RuntimeSharedConversions {
  import com.fortysevendeg.exercises._

  // not particularly clean, but this assigns colors
  // to libraries that don't have a default color provided
  // TODO: make this nicer
  def colorize(libraries: List[Library]): List[Library] = {
    libraries
    val autoPalette = List(
      "#00587A",
      "#44BBFF",
      "#EBF680",
      "#66CC99",
      "#FCA65F",
      "#112233",
      "#FC575E",
      "#CDCBA6",
      "#37465D",
      "#DD6F47",
      "#6AB0AA",
      "#008891",
      "#0F3057"
    )

    val (_, res) = libraries.foldLeft((autoPalette, Nil: List[Library])) { (acc, library) ⇒
      val (colors, librariesAcc) = acc
      if (library.color.isEmpty) {
        val (color, colors0) = colors match {
          case head :: tail ⇒ Some(head) → tail
          case Nil          ⇒ None → Nil
        }
        colors0 → (DefaultLibrary(
          name = library.name,
          description = library.description,
          color = color,
          sections = library.sections
        ) :: librariesAcc)
      } else
        colors → (library :: librariesAcc)
    }
    res.reverse
  }

  def convertLibrary(library: Library) =
    shared.Library(
      name = library.name,
      description = library.description,
      color = library.color getOrElse "black",
      sectionNames = library.sections.map(_.name)
    )

  def convertSection(section: Section) =
    shared.Section(
      name = section.name,
      description = section.description,
      exercises = section.exercises.map(convertExercise)
    )

  def convertExercise(exercise: Exercise) =
    shared.Exercise(
      method = exercise.qualifiedMethod, // exercise.eval Option[type Input => Unit]
      name = exercise.name,
      description = exercise.description,
      code = exercise.code,
      explanation = exercise.explanation
    )

}
