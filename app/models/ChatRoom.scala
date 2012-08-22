package models


import akka.actor._
import akka.util.duration._

import play.api._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._

import akka.util.Timeout
import akka.pattern.ask


import play.api.Play.current

object Robot {
  def apply(chatRoom: ActorRef) {

    val loggerIteratee = Iteratee.foreach[JsValue](event => Logger("robot").info(event.toString))

    implicit val timeout = Timeout(1 second)

    chatRoom ? (Join("Robot")) map {
      case Connected(robotChannel) =>
        //Apply this Enumerator on the logger.
        robotChannel !>> loggerIteratee
    }

    //Make the robot talk every 30 seconds
    Akka.system.scheduler.schedule(
      30 seconds,
      30 seconds,
      chatRoom,
      Talk("Robot", "I'm still alive")
    )
  }
}

object ChatRoom {
  implicit val timeout = Timeout(1 second)

  lazy val default = {
    val roomActor = Akka.system.actorOf(Props[ChatRoom])

    Robot(roomActor)
    roomActor
  }

  def join(username:String):Promise[(Iterate[JsValue, _], Enumerator[JsValue]] = {
      (default ? Join(username)).asPromise.map {
        case Connected(enumerator) =>
        val iteratee = Iteratee.forEach[JsValue] { event =>
          default ! Talk(username, (event \ "text").as[String] )
        }.mapDone { _ =>
          default ! Quit(username)
        }

        (iteratee, enumerator)o

        case CannotConnect(error) =>
          
        val iteratee = Done[JsValue, Unit]((), Input.EOF)
        val enumerator = Enumerator[JsValue](JsObject(Seq("error" -> JsString(error)))).andThen(Enumerator.enumInput(Input.EOF))
        (iteratee, enumerator)

      }

  }
}
