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

package org.apache.flink.training.exercises.longrides.scala

import java.time.Duration
import java.time.Instant

import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.state.ValueState
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.functions.sink.PrintSinkFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.training.exercises.common.datatypes.TaxiRide
import org.apache.flink.training.exercises.common.sources.TaxiRideGenerator
import org.apache.flink.util.Collector
import org.apache.flinkx.api._
import org.apache.flinkx.api.serializers._

/** The "Long Ride Alerts" exercise.
  *
  * <p>The goal for this exercise is to emit the rideIds for taxi rides with a
  * duration of more than two hours. You should assume that TaxiRide events can
  * be lost, but there are no duplicates.
  *
  * <p>You should eventually clear any state you create.
  */
object LongRidesExercise {

  implicit val taxiRideTypeInfo: TypeInformation[TaxiRide] =
    TypeInformation.of(classOf[TaxiRide])

  class LongRidesJob(
      source: SourceFunction[TaxiRide],
      sink: SinkFunction[Long]
  ) {

    /** Creates and executes the ride cleansing pipeline.
      */
    @throws[Exception]
    def execute(): JobExecutionResult = {
      val env = StreamExecutionEnvironment.getExecutionEnvironment

      // start the data generator
      val rides = env.addSource(source)

      // the WatermarkStrategy specifies how to extract timestamps and generate watermarks
      val watermarkStrategy = WatermarkStrategy
        .forBoundedOutOfOrderness[TaxiRide](Duration.ofSeconds(60))
        .withTimestampAssigner(new SerializableTimestampAssigner[TaxiRide] {
          override def extractTimestamp(
              ride: TaxiRide,
              streamRecordTimestamp: Long
          ): Long =
            ride.getEventTimeMillis
        })

      // create the pipeline
      rides
        .assignTimestampsAndWatermarks(watermarkStrategy)
        .keyBy(_.rideId)
        .process(new AlertFunction())
        .addSink(sink)

      // execute the pipeline and return the result
      env.execute("Long Taxi Rides")
    }

  }

  @throws[Exception]
  def main(args: Array[String]): Unit = {
    val job = new LongRidesJob(new TaxiRideGenerator, new PrintSinkFunction)

    job.execute()
  }

}

class AlertFunction extends KeyedProcessFunction[Long, TaxiRide, Long] {

  type Self = KeyedProcessFunction[Long, TaxiRide, Long]

  lazy val state: ValueState[AlertFunctionState] =
    getRuntimeContext().getState(
      new ValueStateDescriptor[AlertFunctionState](
        "InProgressState",
        classOf[AlertFunctionState],
        AlertFunctionState.NullState(Instant.now().toEpochMilli())
      )
    )

  override def processElement(
      ride: TaxiRide,
      ctx: Self#Context,
      out: Collector[Long]
  ): Unit = {

    state.value() match {
      case AlertFunctionState.NullState(_)
          if ride.isStart => // правильный порядок - старт перед стопом
        val startTime = ride.getEventTimeMillis()
        val alarmTime: Long = getAlarmTime(startTime)
        ctx.timerService().registerEventTimeTimer(alarmTime)

        state.update(AlertFunctionState.StartState(startTime))

      case AlertFunctionState.NullState(
            _
          ) /* if ride.isStop */ => // неправильный порядок - стоп перед стартом
        val endRideTime = ride.getEventTimeMillis()
        state.update(AlertFunctionState.EndBeforeState(endRideTime))

      case AlertFunctionState.StartState(startTime)
          if ride.isStop => // правильный порядок - стоп после старта
        // если событие end пришло после start и таймер не сработал - выключаем все
        val alarmTime: Long = getAlarmTime(startTime)
        setStopped(ctx, Some(alarmTime))

      case AlertFunctionState.StartState(
            startTime
          ) /*if ride.isStart*/ => // что-то странное - старт пришел второй раз
        // если событие start пришло второй раз
        println(
          s"WARN start event second time[${ride.getEventTimeMillis()}], prev startTime[$startTime] ============"
        )
      // TODO: update start time or setStopped ?

      case AlertFunctionState.EndBeforeState(endRideTime)
          if ride.isStart => // неправильный порядок - старт после стопа
        val startTime = ride.getEventTimeMillis()

        if (Duration.ofMillis(endRideTime - startTime).getSeconds() > 3600) {
          out.collect(ctx.getCurrentKey())
        }

        setStopped(ctx, None)

      case AlertFunctionState
            .EndBeforeState( // что-то странное - стоп пришел второй раз
              endRideTime
            ) /* if ride.isStop */ =>
        println(
          s"WARN stop event arrived second time[${ride.getEventTimeMillis()}], prev endRideTime[$endRideTime] ============"
        )

      // TODO: update stop time or setStopped ?

      case AlertFunctionState.FinishState(_) =>
        println(
          s"WARN an event arrived after FINISH state time[${ride.getEventTimeMillis()}] ============"
        )
    }
  }

  def getAlarmTime(ts: Long) =
    Instant
      .ofEpochMilli(ts)
      .plus(Duration.ofHours(2))
      .toEpochMilli()

  def setStopped(ctx: Self#Context, alarmTime: Option[Long]) = {
    // безусловная остановка таймера (если запущен)
    // и установка флага что поездка закончена
    alarmTime.foreach(ctx.timerService().deleteEventTimeTimer)
    state.update(AlertFunctionState.FinishState(alarmTime.getOrElse(0L)))
  }

  override def onTimer(
      timestamp: Long,
      ctx: Self#OnTimerContext,
      out: Collector[Long]
  ): Unit = {
    state.value() match {
      case AlertFunctionState.StartState(startTime) =>
        val key = ctx.getCurrentKey()

        println(
          s"WARN a LONG ride[$key],s[$startTime],e[$timestamp], duration[" +
            s"${Duration.ofMillis(timestamp - startTime).toSeconds()}]"
        )
        out.collect(key)

      //  setStopped(ctx, None)
      case other =>
        println(
          s"WARN a timer fires in state[${other.toString()}]"
        )
    }

  }

}

sealed trait AlertFunctionState extends Product with Serializable

object AlertFunctionState {
  //WARN: использование case object вызовет MatchError
  final case class NullState(creationTime: Long) extends AlertFunctionState
  final case class StartState(startTime: Long) extends AlertFunctionState

  final case class EndBeforeState(endTime: Long) extends AlertFunctionState

  final case class FinishState(finishTime: Long) extends AlertFunctionState

  implicit val eventTypeInfo: TypeInformation[AlertFunctionState] =
    deriveTypeInformation

}
