package daut0_experiments.Requirements

import daut0_experiments.Events._
import daut0_experiments.Steps._

object Requirements {
  //
  // Steps
  //													eventName							isInRange																untilStep	untilState		conditionStep		stepName		dtStable	timeout
  var Step_02_01 = new Observe_03[String, Boolean, Int](
    "SetDapMode", 						(x: Option[String]) 	=> x == Some("normal"),							None, 		None,
    "SetDapActive", 					(x: Option[Boolean]) 	=> x == Some(true), 							None,		None,
    "SetDapSetPointAsMeter", 			(x: Option[Int]) 		=> x == Some(100), 								None, 		None, 			None,				"Step_02_01", 	2, 			10 		)

  var Step_03_01 = new Observe_01[Int](				"StatusCrtFloodingAsLiter", 		(x: Option[Int]) 		=> x == Some(2000),								None, 		None, 			Some(Step_02_01),	"Step_03_01", 	2, 			10 		)
  var Step_03_02 = new Observe_01[Int](				"SetI18OpenAsPercentage", 			(x: Option[Int]) 		=> x == Some(1), 								None, 		None, 			Some(Step_03_01),	"Step_03_02", 	2, 			10 		)

  var Step_04_01 = new Observe_01[Float]( 			"StatusDepthAsMeter", 				(x: Option[Float]) 		=> x != None && 95 <= x.get && x.get <= 105,	None, 		None, 			Some(Step_03_02),	"Step_04_01", 	2, 			10 		)
  var Step_04_02 = new Observe_01[Boolean]( 			"SetDapActive", 					(x: Option[Boolean]) 	=> x == Some(false), 							None,		None, 			Some(Step_04_01),	"Step_04_02", 	2, 			10 		)
  var Step_04_03 = new Observe_01[String](			"StatusOperationMode", 				(x: Option[String]) 	=> x == Some("manual"), 						None, 		None,			Some(Step_04_02),	"Step_04_03", 	2, 			10 		)

  var Step_05_01 = new Observe_01[Int](				"SetTrimForwardAsLiter", 			(x: Option[Int]) 		=> x == Some(200),								None, 		None, 			Some(Step_04_03),	"Step_05_01", 	2, 			10 		)
  var Step_05_02 = new Observe_02[Int, Boolean](		"StatusTrimmedForwardAsLiter", 		(x: Option[Int]) 		=> x != None && 199 <= x.get && x.get <= 201,	None, 		None,
    "StatusPitchGoesDown", 				(x: Option[Boolean]) 	=> x == Some(true), 							None, 		None,  			Some(Step_05_01),	"Step_05_02", 	2, 			10		)

  var Step_06_01 = new Observe_01[Int](				"SetFloodTorpedoTubeAsId", 			(x: Option[Int]) 		=> x == Some(1),								None,		None, 			Some(Step_05_02),	"Step_06_01", 	2, 			10 		)
  var Step_06_02 = new Observe_02[Boolean, Boolean](
    "StatusIsTorpedoTube01Flooded", 	(x: Option[Boolean]) 	=> x == Some(true),								None, 		None,
    "StatusPitchGoesDown", 				(x: Option[Boolean]) 	=> x == Some(true), 							None, 		None,  			Some(Step_06_01),	"Step_06_02", 	2, 			10		)

  var Step_07_01 = new Observe_01[String](			"SetCrewMoving", 					(x: Option[String]) 	=> x == Some("FORWARD"),						None, 		None, 			Some(Step_06_02),	"Step_07_01", 	2, 			10 		)
  var Step_07_02 = new Observe_02[String, Boolean](
    "StatusCrewMoving", 				(x: Option[String]) 	=> x == Some("FORWARD"),						None, 		None,
    "StatusPitchGoesDown", 				(x: Option[Boolean]) 	=> x == Some(true), 							None, 		None,  			Some(Step_07_01),	"Step_07_02", 	2, 			10		)

  var Step_08_01 = new Observe_01[Float](				"SetPropulsionAsRpm", 				(x: Option[Float]) 		=> x == Some(24),								None, 		None, 			Some(Step_07_02),	"Step_08_01", 	2, 			10 		)
  var Step_08_02 = new Observe_01[Float](				"StatusPropulsionAsRpm", 			(x: Option[Float]) 		=> x != None && 23.5 <= x.get && x.get <= 24.5,	None, 		None, 			Some(Step_08_01),	"Step_08_02", 	2, 			10 		)

  var Step_09_01 = new Observe_01[Float]( 			"StatusDepthAsMeter", 				(x: Option[Float]) 		=> x != None && 125 <= x.get && x.get <= 135,	None, 		None, 			Some(Step_08_02),	"Step_09_01", 	2, 			10 		)
  var Step_09_02 = new Observe_01[Float]( 			"SetPropulsionAsRpm", 				(x: Option[Float]) 		=> x == Some(0), 								None, 		None, 			Some(Step_09_01),	"Step_09_02", 	2, 			10 		)
  var Step_09_03 = new Observe_01[Float](				"StatusPropulsionAsRpm", 			(x: Option[Float]) 		=> x == Some(0), 								None, 		None, 			Some(Step_09_02),	"Step_09_03", 	2, 			10 		)

  var Step_10_01 = new Observe_03[Float, Float, Float](
    "StatusPitchAsDeg", 				(x: Option[Float]) 		=> x != None && 3 <= x.get && x.get <= 7,		None, 		None,
    "StatusVerticalSpeedAsMPerSec", 	(x: Option[Float]) 		=> x != None && 0.1 <= x.get && x.get <= 0.3,	None, 		None,
    "StatusHorizontalSpeedAsMPerSec", 	(x: Option[Float]) 		=> x != None && 0.0 <= x.get && x.get <= 0.1,	None, 		None, 			Some(Step_09_03),	"Step_10_01", 	1, 			10 		)
  var Step_10_02 = new Observe_01[Boolean](			"StatusBottomed", 					(x: Option[Boolean]) 	=> x == Some(true), 							None, 		None, 			Some(Step_10_01),	"Step_10_02", 	1, 			10 		)
  var Step_10_03 = new Observe_01[Float](				"StatusDepthAsMeter", 				(x: Option[Float]) 		=> x != None && 145 <= x.get && x.get <= 155, 	None, 		None, 			Some(Step_10_02),	"Step_10_03", 	1, 			10 		)

  var Step_11_01 = new Observe_02[Int, Int](
    "SetBilgePump", 					(x: Option[Int]) 		=> x == Some(2), 								None, 		None,
    "SetDrainToSeaCrt01", 				(x: Option[Int]) 		=> x == Some(2000), 							None, 		None, 			Some(Step_10_03),	"Step_11_01", 	2, 			10 		)
  var Step_11_02 = new Observe_01[Int](				"StatusDrainToSeaCrt01", 			(x: Option[Int]) 		=> x != None && 1999 <= x.get && x.get <= 2001, None, 		None, 			Some(Step_11_01),	"Step_11_02", 	2, 			10 		)

  var Step_12_01 = new Observe_02[Int, Int](
    "SetBilgePump", 					(x: Option[Int]) 		=> x == Some(2), 								None, 		None,
    "SetDrainToSeaCrt01", 				(x: Option[Int]) 		=> x == Some(500), 								None, 		None, 			Some(Step_11_02),	"Step_12_01", 	2, 			10 		)
  var Step_12_02 = new Observe_01[Int](				"StatusDrainToSeaCrt01", 			(x: Option[Int]) 		=> x != None && 499 <= x.get && x.get <= 501, 	None, 		None, 			Some(Step_12_01),	"Step_12_02", 	2, 			10 		)
  //var Step_12_03 = new Switch_01[Boolean]			"StatusUnbottoming",
  var Step_12_03 = new Observe_01[Boolean](			"StatusUnbottoming", 				(x: Option[Boolean]) 	=> x == Some(true), 							None, 		None, 			Some(Step_12_02),	"Step_12_03", 	2, 			10 		)

  var Step_13_01 = new Observe_01[Int](				"SetTrimAftAsLiter",				(x: Option[Int]) 		=> x == Some(200),								None, 		None, 			Some(Step_12_03),	"Step_13_01", 	2, 			10 		)
  var Step_13_02 = new Observe_02[Int, Boolean](
    "StatusTrimAftAsLiter", 			(x: Option[Int]) 		=> x != None && 199 <= x.get && x.get <= 201,	None, 		None,
    "StatusPitchGoesUp", 				(x: Option[Boolean]) 	=> x == Some(true), 							None, 		None,  			Some(Step_13_01),	"Step_13_02", 	2, 			10		)

  var Step_14_01 = new Observe_01[Int](				"SetDrainTorpedoTubeAsId",			(x: Option[Int]) 		=> x == Some(1),								None, 		None, 			Some(Step_13_02),	"Step_14_01", 	2, 			10 		)
  var Step_14_02 = new Observe_02[Boolean, Boolean](
    "StatusIsTorpedoTube01Drained", 	(x: Option[Boolean]) 	=> x == Some(true),								None, 		None,
    "StatusPitchGoesUp", 				(x: Option[Boolean]) 	=> x == Some(true), 							None, 		None,  			Some(Step_14_01),	"Step_14_02", 	2, 			10		)

  var Step_15_01 = new Observe_01[String](			"SetCrewMoving", 					(x: Option[String]) 	=> x == Some("CENTER"),							None, 		None, 			Some(Step_14_02),	"Step_15_01", 	2, 			10 		)
  var Step_15_02 = new Observe_02[String, Boolean](
    "StatusCrewMoving", 				(x: Option[String]) 	=> x == Some("CENTER"),							None, 		None,
    "StatusPitchGoesUp", 				(x: Option[Boolean]) 	=> x == Some(true), 							None, 		None,  			Some(Step_15_01),	"Step_15_02", 	2, 			10		)

  var Step_16_01 = new Observe_01[Float](				"SetPropulsionAsRpm", 				(x: Option[Float]) 		=> x == Some(60),								None, 		None, 			Some(Step_15_02),	"Step_16_01", 	2, 			10 		)
  var Step_16_02 = new Observe_01[Float](				"StatusPropulsionAsRpm", 			(x: Option[Float]) 		=> x != None && 59.5 <= x.get && x.get <= 60.5,	None, 		None, 			Some(Step_16_01),	"Step_16_02", 	2, 			10 		)

  var Step_17_01 = new Observe_01[Float]( 			"StatusDepthAsMeter", 				(x: Option[Float]) 		=> x != None && 49 <= x.get && x.get <= 51,		None, 		None, 			Some(Step_16_02),	"Step_17_01", 	2, 			10 		)
  //
  // Until statements (tbd: should be doable in above table instead - see untilStep, untilState)
  //
  //Step_02_01.untilStep02 = Some(Step_04_01)
  //Step_02_01.untilState02 = Some( "Active" )
  //
  //Step_07_02.untilStep01 = Some(Step_15_01)
  //Step_07_02.untilState01 = Some( "Active" )

  Step_10_01.untilStep01 = Some(Step_10_02)
  Step_10_01.untilState01 = Some( "Done" )
  //
  // All requirements
  //
  var instance = List[Step](
    Step_02_01,
    Step_03_01, Step_03_02,
    Step_04_01, Step_04_02, Step_04_03,
    Step_05_01, Step_05_02,
    Step_06_01, Step_06_02,
    Step_07_01, Step_07_02,
    Step_08_01, Step_08_02,
    Step_09_01, Step_09_02, Step_09_03,
    Step_10_01, Step_10_02, Step_10_03,
    Step_11_01, Step_11_02,
    Step_12_01, Step_12_02, Step_12_03, // tbd: switch
    Step_13_01, Step_13_02,
    Step_14_01, Step_14_02,
    Step_15_01, Step_15_02,
    Step_16_01, Step_16_02,
    Step_17_01
  )
//  instance.map( x => x.Init() )
}

// NOTE
// PARTLY OPEN - needs switch
// Req. 12.1 (SETTING) - Value drainToSea shall be set to 500 liter, CRT 1, bilgePump 2.
// Req. 12.2 (STATUS) - Value drainedToSea shall be 500 liter, CRT 1.
// Req. 12.3 (SETTING) - (see 12.1)
// Req. 12.4 (STATUS) - (see 12.2)
// Req. 12.5 (STATUS->SETTING) - If value unbottoming is not true for 3 sec. repeat 12.3, 12.4, 12.5. <-- ABSTRACTION 'unbottoming'
// Req. 12.6 (STATUS) - Value unbottoming is true.


// ---------------------------------------------------------------------------------------------------------------------------------------------
//
// Step 2
// Title: Steering Mode Selection
//
// Short version:
// 1. Select the DAP mode normal.
// 2. Activate the DAP.
// 3. Set the DAP set point to 100m.
// 4. DAP is activated and in normal mode.
//
// Detailed version:
// Req 2.1 (SETTING) - Value DAP mode shall be set to normal [exactly].
// Req 2.2 (SETTING) - Value DAP shall be set to active [exactly].
// Req 2.3 (SETTING) - Value DAP set point shall be set to 100 m [exactly].
// [Order of requirements is (), i.e. arbitrary]
// [Timeout for step is 10 sec]

// ---------------------------------------------------------------------------------------------------------------------------------------------
//
// Step 3
// Title: Compensating System
//
// Short version:
// 1. Flooding CRT1 with 2000l (note: left out the constraints doing this - reason: not fully understood).
// 2. CRT1 is flooded with 2000l.
// 3. Open I18 1%.
//
// Detailed version:
// Req 3.2 (STATUS) - Value CRT1 flooding shall be 2000 liter [+/- 1 liter].
// Req 3.3 (SETTING) - Value I18 shall be set to 1 percent [exactly]
// [Order of requirements is (3.2, 3.3)]
// [Timeout for step is 30 sec]
//

// ---------------------------------------------------------------------------------------------------------------------------------------------
//
// Step 4
// Title: Steering Mode
//
// Short version:
// 1. At depth 100m deactivate the DAP.
// 2. Manual operation mode is activated.
//
// Detailed version:
// Req. 4.1 (STATUS) - Value depth shall become 100 m [+/- 5 m] [within 10 seconds].
// Req. 4.2 (SETTING) - Value DAP mode shall be set to deactivated [exactly] [within 10 seconds].
// Req. 4.3 (STATUS) - Value operation mode shall be manual [exactly].
// [Order of requirements is (4.1, 4.2, 4.3)]
// [Timeout for step is 30 sec]
//

// ---------------------------------------------------------------------------------------------------------------------------------------------
//
// Step 5
// Title: Trimming System
//
// Short version:
// 1. Trimming of 200l to forward.
// 2. Forward trim 200l.
// 3. Pitch increase to down.
//
// Detailed version:
// Req. 5.1 (SETTING) - Value trim forward shall be set to 200 liter [exactly].
// Req. 5.2 (STATUS) - Value trimmedForward shall be 200 liter [+/- 1 liter]. <-- ABSTRACTION of 'trimmedForward'
// Req. 5.3 (STATUS) - Value pitch shall go down [exactly]. <-- ABSTRACTION of 'statusPitchGoesDown'
// [Order of requirements is (5.1, 5.2), (5.1, 5.3)]
// [Timeout for step is 30 sec]
//

// ---------------------------------------------------------------------------------------------------------------------------------------------
//
// Step 6
// Title: Torpedo System
//
// Short version:
// 1. Flood torpedo tube 1
// 2. Torpedo tube 1 is flooded.
// 3. Pitch increase to down.
//
// Detailed version:
// Req. 6.1 (SETTING) - Value flood torpedo tube shall be set to ID 1 [exactly].
// Req. 6.2 (STATUS) - Value torpedo tube flooded shall be true for ID 1 [exactly].
// Req. 6.2 (STATUS) - Value pitch shall go down [exactly].
// [Order of requirements is (6.1, 6.2), (6.1, 6.3)]
// [Timeout for step is 30 sec]
//

// ---------------------------------------------------------------------------------------------------------------------------------------------
//
// Step 7
// Title: Crew Moving
//
// Short version:
// 1. Set crew moving to forward.
// 2. Crew moves to forward.
// 3. Pitch increase to down.
//
// Detailed version:
// Req. 7.1 (SETTING) - Value crew moving shall be set to forward [exactly].
// Req. 7.2 (STATUS) - Value crew moving shall be forward [exactly].
// Req. 7.2 (STATUS) - Value pitch shall go down [exactly].
// [Order of requirements is (7.1, 7.2), (7.1, 7.3)]
// [Timeout for step is 30 sec]
//

// ---------------------------------------------------------------------------------------------------------------------------------------------
//
// Step 8
// Title: Propulsion
//
// Short version:
// 1. Set the RPM to 24rpm
// 2. Propulsion is 24rpm.
//
// Detailed version:
// Req. 8.1 (SETTING) - Value propulsion shall be set to 24 rpm [exactly].
// Req. 8.2 (STATUS) - Value propulsion shall be 24 rpm [+/- 0.5].
// [Order of requirements is (8.1, 8.2)]
// [Timeout for step is 10 sec]
//

// ---------------------------------------------------------------------------------------------------------------------------------------------
//
// Step 9
// Title: Propulsion
//
// Short version:
// 1. At depth 130m set the RPM to 0.0.
// 2. RPM = 0.0
//
// Detailed version:
// Req. 9.1 (STATUS) - Value depth shall become 130 m [+/- 5 m] [within 10 seconds].
// Req. 9.2 (SETTING) - Value propulsion shall be set to 0 rpm [exactly] [within 10 seconds].
// Req. 9.3 (STATUS) - Value propulsion shall be 0 rpm [exactly] [within 10 seconds].
// [Order of requirements is (9.1, 9.2, 9.3)]
// [Timeout for step is 30 sec]
//

// ---------------------------------------------------------------------------------------------------------------------------------------------
//
// Step 10
// Title: Steering Control
//
// Short version:
// 1. Control the submarine to the ground under conditions
// 1.1 approx. pitch 5° down
// 1.2 0.1-0.3m/s vertical speed
// 1.3 approx. 0.0m/s horizontal speed
// 2. Bottoming at 150m depth.
// 3. The submarine stays fixed on the seabed.
//
// Detailed version:
// Req. 10.1.1 (STATUS) - Value pitch shall be 5 deg down [+/- 2 deg] UNTIL bottoming. <-- ABSTRACTION 'bottoming'
// Req. 10.1.2 (STATUS) - Value vertical speed shall be 0.2 m/s [+/- 0.1 m/s] UNTIL bottoming.
// Req. 10.1.3 (STATUS) - Value horizontal speed shall be 0.0 m/s [+ 0.1 m/s] UNTIL bottoming.
// Req. 10.2 (STATUS) - If above values are adjusted, then EVENTUALLY value bottoming shall be true [exactly].
// Req. 10.3 (STATUS) - If value bottoming is true, then depth shall be 150 m [+/- 5 m].
// [Order of requirements is (10.1.1 - 10.1.3, 10.2), (10.2, 10.3)]
// [Timeout for step is 180 sec]
//

// ---------------------------------------------------------------------------------------------------------------------------------------------
//
// Step 11
// Title: Compensating System
//
// Short version:
// 1. Draining of 2000l (CRT1) to sea.
// 2. Use bilge pump 2.
// 3. 2000l is draining to sea.
//
// Detailed version:
// Req. 11.1 (SETTING) - Value drainToSea shall be set to 2000 liter, CRT 1, bilgePump 2.
// Req. 11.2 (STATUS) - Value drainedToSea shall be 2000 liter, CRT 1. <-- ABSTRACTION 'drainedToSea'
// [Order of requirements is (11.1, 11.2)]
// [Timeout for step is 120 sec]
//

// ---------------------------------------------------------------------------------------------------------------------------------------------
//
// Step 12
// Title: Compensating System
//
// Short version:
// 1. Draining of 500l (CRT1) to sea.
// 2. 500l is draining to sea.
// 3. Draining of 500l (CRT1) to sea.
// 4. 500l is draining to sea.
// 5. Repeat draining 500l until unbottoming starts.
// 6. Unbottoming of the submarine starts.
//
// Detailed version:
// Req. 12.1 (SETTING) - Value drainToSea shall be set to 500 liter, CRT 1, bilgePump 2.
// Req. 12.2 (STATUS) - Value drainedToSea shall be 500 liter, CRT 1 [+/- 1 liter].
// Req. 12.3 (SETTING) - (see 12.1)
// Req. 12.4 (STATUS) - (see 12.2)
// Req. 12.5 (STATUS->SETTING) - If value unbottoming is not true for 3 sec. repeat 12.3, 12.4, 12.5. <-- ABSTRACTION 'unbottoming'
// Req. 12.6 (STATUS) - Value unbottoming is true.
// [Order of requirements is (12.1, 12.2, 12.3, 12.4, 12.5, 12.6)]
// [Timeout for step is 120 sec]
//

// ---------------------------------------------------------------------------------------------------------------------------------------------
//
// Step 13
// Title: Trimming System
//
// Short version:
// 1. Trimming of 200l to aft
// 2. Aft trim 200l.
// 3. Pitch decrease to up.
//
// Detailed version:
// Req. 13.1 (SETTING) - Value trim aft shall be set to 200 liter [exactly].
// Req. 13.2 (STATUS) - Value trimmedAft shall be 200 liter [+/- 1 liter]. <-- ABSTRACTION 'trimmedAft'
// Req. 13.3 (STATUS) - Value pitch shall go up [exactly]. <-- ABSTRACTION 'pitchGoesUp'
// [Order of requirements is (13.1, 13.2), (13.1, 13.3)]
// [Timeout for step is 30 sec]
//

// ---------------------------------------------------------------------------------------------------------------------------------------------
//
// Step 14
// Title: Torpedo System
//
// Short version:
// 1. Drain torpedo tube 1
// 2. Torpedo tube 1 is drained.
// 3. Pitch decrease to up.
//
// Detailed version:
// Req. 14.1 (SETTING) - Value drain torpedo tube shall be set to ID 1 [exactly].
// Req. 14.2 (STATUS) - Value torpedo tube drained shall be true for ID 1 [exactly].
// Req. 14.2 (STATUS) - Value pitch shall go up [exactly].
// [Order of requirements is (14.1, 14.2), (14.1, 14.3)]
// [Timeout for step is 30 sec]
//

// ---------------------------------------------------------------------------------------------------------------------------------------------
//
// Step 15
// Title: Crew Moving
//
// Short version:
// 1. Set crew moving to center.
// 2. Crew moves to center.
// 3. Pitch decreases to up.
//
// Detailed version:
// Req. 15.1 (SETTING) - Value crew moving shall be set to center [exactly].
// Req. 15.2 (STATUS) - Value crew moving shall be center [exactly].
// Req. 15.2 (STATUS) - Value pitch shall go up [exactly].
// [Order of requirements is (15.1, 15.2), (15.1, 15.3)]
// [Timeout for step is 30 sec]
//

// ---------------------------------------------------------------------------------------------------------------------------------------------
//
// Step 16
// Title: Propulsion
//
// Short version:
// 1. Set the RPM to 60rpm
// 2. Propulsion is 60rpm.
//
// Detailed version:
// Req. 16.1 (SETTING) - Value propulsion shall be set to 60 rpm [exactly].
// Req. 16.2 (STATUS) - Value propulsion shall be 60 rpm [+/- 0.5].
// [Order of requirements is (16.1, 16.2)]
// [Timeout for step is 10 sec]
//

// ---------------------------------------------------------------------------------------------------------------------------------------------
//
// Step 17
// Title: Steering Control
//
// Short version:
// 1. Control the submarine to 50 meter
//
// Detailed version:
// Req. 17.1 (STATUS) - Value depth shall be 50 meter [exactly].
// [Order of requirements is ()]
// [Timeout for step is 240 sec]
//

// ---------------------------------------------------------------------------------------------------------------------------------------------
//
// END OF TRAINING EXERCISE
//
// ---------------------------------------------------------------------------------------------------------------------------------------------
