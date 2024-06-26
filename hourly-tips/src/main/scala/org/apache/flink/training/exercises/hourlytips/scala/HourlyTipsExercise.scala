/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.training.exercises.hourlytips.scala

import java.time._
import java.time.temporal.ChronoUnit

import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.api.common.state.ValueState
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.functions.sink.PrintSinkFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.training.exercises.common.datatypes.TaxiFare
import org.apache.flink.training.exercises.common.sources.TaxiFareGenerator
import org.apache.flink.util.Collector
import org.apache.flinkx.api._
import org.apache.flinkx.api.serializers._

/** The Hourly Tips exercise from the Flink training.
  *
  * The task of the exercise is to first calculate the total tips collected by each driver, hour by hour, and then from that stream, find the highest tip total
  * in each hour.
  */
object HourlyTipsExercise {

  implicit val taxiFareTypeInfo: TypeInformation[TaxiFare] = TypeInformation.of(classOf[TaxiFare])

  @throws[Exception]
  def main(args: Array[String]): Unit = {
    val job = new HourlyTipsJob(new TaxiFareGenerator, new PrintSinkFunction)

    job.execute()
  }

  class HourlyTipsJob(source: SourceFunction[TaxiFare], sink: SinkFunction[(Long, Long, Float)]) {

    /** Create and execute the ride cleansing pipeline.
      */
    @throws[Exception]
    def execute(): JobExecutionResult = {

      val env = StreamExecutionEnvironment.getExecutionEnvironment

      // start the data generator
      val fares: DataStream[TaxiFare] = env.addSource(source)

      // Canonical !
      // fares
      //   .assignAscendingTimestamps(_.getEventTimeMillis)
      //   .map { taxiFare =>
      //     taxiFare.driverId -> taxiFare.tip
      //   }
      //   .keyBy {
      //     case (driverId, _) => driverId
      //   }
      //   .window(
      //     TumblingEventTimeWindows.of(Time.hours(1))
      //   )
      //   .reduce {
      //     case ((driverId, tipLeft), (_, tipRight)) =>
      //       driverId -> (tipLeft + tipRight)
      //   }
      //   .windowAll(TumblingEventTimeWindows.of(Time.hours(1)))
      //   .max(1)
      // .print()

      // Custom : TODO: is it correct ?
      fares
        .assignAscendingTimestamps(_.getEventTimeMillis)
        .map { taxiFare =>
          val startHour = MyProcFunc.startHour(taxiFare.getEventTimeMillis())
          (startHour, taxiFare.driverId, taxiFare.tip)
        }
        .keyBy {
          case (startHour, driverId, _) => startHour -> driverId
        }
        .process(new MyProcFunc())
        .keyBy {
          case (endOfHour, _, _) => endOfHour
        }
        .max(2)
        .print()

      // the results should be sent to the sink that was passed in
      // (otherwise the tests won't work)
      // you can end the pipeline with something like this:

      // val hourlyMax = ...
      // hourlyMax.addSink(sink);

      // execute the pipeline and return the result
      env.execute("Hourly Tips")
    }

  }

}

class MyProcFunc extends KeyedProcessFunction[(Long, Long), (Long, Long, Float), (Long, Long, Float)] {

  /** The state that is maintained by this process function */
  lazy val sumOfTipsState: ValueState[Float] =
    getRuntimeContext().getState(new ValueStateDescriptor("myState", classOf[Float]))

  lazy val firstInState: ValueState[Boolean] = {
    val ret =
      getRuntimeContext().getState(new ValueStateDescriptor("FirstInState", classOf[Boolean]))
    ret.update(true)
    ret
  }

  def processElement(
      elem: (Long, Long, Float),
      ctx: KeyedProcessFunction[(Long, Long), (Long, Long, Float), (Long, Long, Float)]#Context,
      out: Collector[(Long, Long, Float)]
  ): Unit = {
    val (_, driverId, tip: Float) = elem
    if (firstInState.value()) {
      val (hour, _) = ctx.getCurrentKey()

      val nextHour = MyProcFunc.endOfHour(hour).toEpochMilli()

      sumOfTipsState.update(tip)

      ctx.timerService().registerEventTimeTimer(nextHour)

      println(s"Timer for nextHour[$nextHour] and driverId[$driverId] has been registered")

    } else {
      sumOfTipsState.update(sumOfTipsState.value() + tip)
    }

  }

  override def onTimer(
      timestamp: Long,
      ctx: KeyedProcessFunction[(Long, Long), (Long, Long, Float), (Long, Long, Float)]#OnTimerContext,
      out: Collector[(Long, Long, Float)]
  ): Unit = {
    val totalHourTips: Float = Option(sumOfTipsState.value()).getOrElse(0f)
    sumOfTipsState.update(0f) // clean

    val (hour, driverId) = ctx.getCurrentKey()

    val endOfHour = MyProcFunc.endOfHour(hour).toEpochMilli()

    println(s"current key ${ctx.getCurrentKey()}, timestamp[$timestamp], endOfHour[$endOfHour]")

    val ret = (endOfHour, driverId, totalHourTips)

    out.collect(ret)

    ctx.timerService().deleteEventTimeTimer(endOfHour)

  }

}

object MyProcFunc {

  def apply() = new MyProcFunc

  def startHour(eventMillis: Long) =
    Instant
      .ofEpochMilli(eventMillis)
      .truncatedTo(ChronoUnit.HOURS)
      .toEpochMilli()

  def endOfHour(eventMillis: Long) =
    Instant
      .ofEpochMilli(eventMillis)
      .plus(Duration.ofHours(1))

}
