package daut0_experiments.EventStream

import daut0_experiments.Events._

object EventStream {

  var instance = List[Event]( // example event stream
    //
    // Step 02
    //
    Time (1),
    Value[String] 		(	1, "SetDapMode", 						Some("normal")		),
    Value[Boolean] 		(	1, "SetDapActive", 						Some(true)			),
    Value[Int] 			(	1, "SetDapSetPointAsMeter", 			Some(100)			),
    Time (2),
    Time (3),
    Time (4),
    //
    // Step 03
    //
    Time (5),
    Value[Int]			(	5, "StatusCrtFloodingAsLiter", 			Some(2000)			),
    Value[Int]			(	5, "SetI18OpenAsPercentage", 			Some(1)				),
    Time (6),
    Time (7),
    //
    // Step 04
    //
    Time (8),
    Value[Float]		(	8, "StatusDepthAsMeter", 				Some(95)			),
    Value[Boolean] 		(	8, "SetDapActive", 						Some(false)			),
    Value[String] 		(	8, "StatusOperationMode", 				Some("manual")		),
    Time (9),
    Time (10),
    //
    // Step 05
    //
    Time (11),
    Value[Int] 			(	11, "SetTrimForwardAsLiter", 			Some(200)			),
    Value[Int] 			(	11, "StatusTrimmedForwardAsLiter", 		Some(200)			),
    Value[Boolean] 		(	11, "StatusPitchGoesDown", 				Some(true)			),
    Time (12),
    Time (13),
    //
    // Step 06
    //
    Time (14),
    Value[Int] 			(	14, "SetFloodTorpedoTubeAsId", 			Some(1)				),
    Value[Boolean]		(	14, "StatusIsTorpedoTube01Flooded", 	Some(true)			),
    Value[Boolean] 		(	14, "StatusPitchGoesDown", 				Some(true)			),
    Time (15),
    Time (16),
    //
    // Step 07
    //
    Time (17),
    Value[String] 		(	17, "SetCrewMoving", 					Some("FORWARD")		),
    Value[String]		(	17, "StatusCrewMoving", 				Some("FORWARD")		),
    Value[Boolean]		(	17, "StatusPitchGoesDown", 				Some(true)			),
    Time (18),
    Time (19),
    //
    // Step 08
    //
    Time (20),
    Value[Float]		(	20, "SetPropulsionAsRpm", 				Some(24)			),
    Value[Float]		(	20, "StatusPropulsionAsRpm", 			Some(24)			),
    Time (21),
    Time (22),
    //
    // Step 09
    //
    Time (23),
    Value[Float] 		(	23, "StatusDepthAsMeter", 				Some(130)			),
    Value[Float]		(	23, "SetPropulsionAsRpm", 				Some(0)				),
    Value[Float]		(	23, "StatusPropulsionAsRpm", 			Some(0)				),
    Time (24),
    Time (25),
    //
    // Step 10
    //
    Time (26),
    Value[Float] 		(	26, "StatusPitchAsDeg", 				Some(5.0f)			),
    Value[Float]		(	26, "StatusVerticalSpeedAsMPerSec", 	Some(0.2f)			),
    Value[Float]		(	26, "StatusHorizontalSpeedAsMPerSec",	Some(0.0f)			),

    Time (27),
    Value[Float] 		(	27, "StatusPitchAsDeg", 				Some(7.0f)			),
    Value[Float]		(	27, "StatusVerticalSpeedAsMPerSec", 	Some(0.3f)			),
    Value[Float]		(	27, "StatusHorizontalSpeedAsMPerSec",	Some(0.1f)			),

    Time (28),
    Value[Float] 		(	28, "StatusPitchAsDeg", 				Some(4.9f)			),
    Value[Float]		(	28, "StatusVerticalSpeedAsMPerSec", 	Some(0.1f)			),
    Value[Float]		(	28, "StatusHorizontalSpeedAsMPerSec",	Some(0.0f)			),

    Value[Boolean]		(	28, "StatusBottomed", 					Some(true)			),
    Value[Float]		(	28, "StatusDepthAsMeter", 				Some(150f)			),
    Time (29),
    Time (30),
    //
    // Step 11
    //
    Time (31),
    Value[Int] 			(	31, "SetBilgePump", 					Some(2)				),
    Value[Int]			(	31, "SetDrainToSeaCrt01", 				Some(2000)			),
    Value[Int]			(	31, "StatusDrainToSeaCrt01", 			Some(2000)			),
    Time (32),
    Time (33),
    //
    // Step 12
    //
    Time (34),
    Value[Int] 			(	34, "SetBilgePump", 					Some(2)				),
    Value[Int]			(	34, "SetDrainToSeaCrt01", 				Some(500)			),
    Value[Int]			(	34, "StatusDrainToSeaCrt01", 			Some(500)			),
    Value[Boolean]		(	34, "StatusUnbottoming", 				Some(true)			),
    Time (35),
    Time (36),
    //
    // Step 13
    //
    Time (37),
    Value[Int]			(	37, "SetTrimAftAsLiter", 				Some(200)			),
    Value[Int]			(	37, "StatusTrimAftAsLiter", 			Some(200)			),
    Value[Boolean]		(	37, "StatusPitchGoesUp", 				Some(true)			),
    Time (38),
    Time (39),
    //
    // Step 14
    //
    Time (40),
    Value[Int]			(	40, "SetDrainTorpedoTubeAsId", 			Some(1)				),
    Value[Boolean]		(	40, "StatusIsTorpedoTube01Drained",		Some(true)			),
    Value[Boolean]		(	40, "StatusPitchGoesUp", 				Some(true)			),
    Time (41),
    Time (42),
    //
    // Step 15
    //
    Time (43),
    Value[String]		(	44, "SetCrewMoving", 					Some("CENTER")		),
    Value[String]		(	44, "StatusCrewMoving",					Some("CENTER")		),
    Value[Boolean]		(	44, "StatusPitchGoesUp", 				Some(true)			),
    Time (45),
    Time (46),
    //
    // Step 16
    //
    Time (47),
    Value[Float]		(	47, "SetPropulsionAsRpm",				Some(60f)			),
    Value[Float]		(	47, "StatusPropulsionAsRpm", 			Some(60f)			),
    Time (48),
    Time (49),
    //
    // Step 17
    //
    Time (50),
    Value[Float]		(	50, "StatusDepthAsMeter", 				Some(50f)			),
    Time (51),
    Time (52),
    //
    // End of exercise
    //
    Time (100)
  )
}
