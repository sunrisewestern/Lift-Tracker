package com.lifttracker.app.domain

object SampleCatalog {
    val canonicalDatasetIds: Map<String, String> = mapOf(
        "0025" to "ex_bench_press",
        "0043" to "ex_back_squat",
        "0032" to "ex_deadlift",
        "0091" to "ex_overhead_press",
        "2330" to "ex_lat_pulldown",
        "0314" to "ex_incline_db_press",
        "0085" to "ex_romanian_deadlift",
        "0178" to "ex_cable_lateral_raise"
    )

    val defaultExercises: List<ExerciseDefinition> = listOf(
        ExerciseDefinition(
            id = "ex_bench_press",
            datasetId = "0025",
            title = "Barbell Bench Press",
            primaryMuscle = MuscleGroup.CHEST,
            secondaryMuscles = listOf(MuscleGroup.ARMS, MuscleGroup.SHOULDERS),
            equipment = EquipmentType.BARBELL,
            targetMuscle = "pectorals",
            bodyPart = "chest",
            imagePath = "images/0025-EIeI8Vf.jpg",
            gifPath = "videos/0025-EIeI8Vf.gif",
            summaryNotes = "Dataset #0025 • Body part: chest • Target: pectorals • Secondary: triceps, shoulders • Equipment: barbell.",
            instructions = listOf(
                "Lie flat on a bench with your feet flat on the ground and your back pressed against the bench.",
                "Grasp the barbell with an overhand grip slightly wider than shoulder-width apart.",
                "Lift the barbell off the rack and hold it directly above your chest with your arms fully extended.",
                "Lower the barbell slowly towards your chest, keeping your elbows tucked in.",
                "Pause for a moment when the barbell touches your chest.",
                "Push the barbell back up to the starting position by extending your arms.",
                "Repeat for the desired number of repetitions."
            ),
            coachingCues = listOf(
                "Drive heels into the floor for leg drive without lifting glutes off the bench.",
                "Keep wrists stacked over elbows and retract shoulder blades.",
                "Maintain upper-back tightness throughout the eccentric and concentric phases."
            )
        ),
        ExerciseDefinition(
            id = "ex_back_squat",
            datasetId = "0043",
            title = "Barbell Full Squat",
            primaryMuscle = MuscleGroup.LEGS,
            secondaryMuscles = listOf(MuscleGroup.CORE, MuscleGroup.LEGS),
            equipment = EquipmentType.BARBELL,
            targetMuscle = "glutes",
            bodyPart = "upper legs",
            imagePath = "images/0043-qXTaZnJ.jpg",
            gifPath = "videos/0043-qXTaZnJ.gif",
            summaryNotes = "Dataset #0043 • Body part: upper legs • Target: glutes • Secondary: quadriceps, hamstrings, calves, core • Equipment: barbell.",
            instructions = listOf(
                "Stand with your feet shoulder-width apart, toes slightly turned out.",
                "Hold the barbell across your upper back, resting it on your traps or rear delts.",
                "Engage your core and keep your chest up as you begin to lower your body down.",
                "Bend at the knees and hips, pushing your hips back and down as if sitting into a chair.",
                "Lower yourself until your thighs are parallel to the ground or slightly below.",
                "Keep your knees in line with your toes and your weight in your heels.",
                "Drive through your heels to stand back up, extending your hips and knees.",
                "Repeat for the desired number of repetitions."
            ),
            coachingCues = listOf(
                "Track knees in line with your toes throughout the descent and ascent.",
                "Keep chest proud and spine neutral from cervical to lumbar vertebrae.",
                "Re-brace at the top of each repetition."
            )
        ),
        ExerciseDefinition(
            id = "ex_deadlift",
            datasetId = "0032",
            title = "Barbell Deadlift",
            primaryMuscle = MuscleGroup.LEGS,
            secondaryMuscles = listOf(MuscleGroup.BACK, MuscleGroup.LEGS),
            equipment = EquipmentType.BARBELL,
            targetMuscle = "glutes",
            bodyPart = "upper legs",
            imagePath = "images/0032-ila4NZS.jpg",
            gifPath = "videos/0032-ila4NZS.gif",
            summaryNotes = "Dataset #0032 • Body part: upper legs • Target: glutes • Secondary: hamstrings, lower back • Equipment: barbell.",
            instructions = listOf(
                "Stand with your feet shoulder-width apart and the barbell on the ground in front of you.",
                "Bend your knees and hinge at the hips to lower your torso and grip the barbell with an overhand grip, hands slightly wider than shoulder-width apart.",
                "Keep your back straight and chest lifted as you drive through your heels to lift the barbell off the ground, extending your hips and knees.",
                "As you stand up straight, squeeze your glutes and keep your core engaged.",
                "Lower the barbell back down to the ground by bending at the hips and knees, keeping your back straight.",
                "Repeat for the desired number of repetitions."
            ),
            coachingCues = listOf(
                "Keep the bar in contact with your legs along the entire vertical path.",
                "Pull slack out of the bar before initiating the pull off the floor.",
                "Hinge hips back first on the descent before bending the knees."
            )
        ),
        ExerciseDefinition(
            id = "ex_overhead_press",
            datasetId = "0091",
            title = "Barbell Seated Overhead Press",
            primaryMuscle = MuscleGroup.SHOULDERS,
            secondaryMuscles = listOf(MuscleGroup.ARMS, MuscleGroup.BACK),
            equipment = EquipmentType.BARBELL,
            targetMuscle = "delts",
            bodyPart = "shoulders",
            imagePath = "images/0091-kTbSH9h.jpg",
            gifPath = "videos/0091-kTbSH9h.gif",
            summaryNotes = "Dataset #0091 • Body part: shoulders • Target: delts • Secondary: triceps, upper back • Equipment: barbell.",
            instructions = listOf(
                "Sit on a bench with your back straight and feet flat on the ground.",
                "Hold the barbell with an overhand grip, slightly wider than shoulder-width apart.",
                "Lift the barbell off the rack and bring it to shoulder level, with your elbows bent and palms facing forward.",
                "Press the barbell overhead by extending your arms fully.",
                "Pause for a moment at the top, then slowly lower the barbell back to shoulder level.",
                "Repeat for the desired number of repetitions."
            ),
            coachingCues = listOf(
                "Brace your core to prevent excessive lumbar extension.",
                "Keep forearms vertical from both front and side views.",
                "Stack barbell, wrists, elbows, and shoulders at lockout."
            )
        ),
        ExerciseDefinition(
            id = "ex_lat_pulldown",
            datasetId = "2330",
            title = "Cable Lat Pulldown Full Range Of Motion",
            primaryMuscle = MuscleGroup.BACK,
            secondaryMuscles = listOf(MuscleGroup.ARMS, MuscleGroup.SHOULDERS),
            equipment = EquipmentType.CABLE,
            targetMuscle = "lats",
            bodyPart = "back",
            imagePath = "images/2330-LEprlgG.jpg",
            gifPath = "videos/2330-LEprlgG.gif",
            summaryNotes = "Dataset #2330 • Body part: back • Target: lats • Secondary: biceps, rhomboids, rear deltoids • Equipment: cable.",
            instructions = listOf(
                "Sit on the lat pulldown machine with your knees positioned under the pads.",
                "Grasp the cable bar with an overhand grip, slightly wider than shoulder-width apart.",
                "Lean back slightly and keep your chest up, maintaining a slight arch in your lower back.",
                "Pull the bar down towards your upper chest, squeezing your shoulder blades together.",
                "Pause for a moment at the bottom of the movement, then slowly release the bar back to the starting position.",
                "Repeat for the desired number of repetitions."
            ),
            coachingCues = listOf(
                "Think of your hands as hooks and lead the movement with your elbows.",
                "Avoid swinging your torso backward to generate momentum.",
                "Allow a full scapular upward rotation at the top of each rep."
            )
        ),
        ExerciseDefinition(
            id = "ex_incline_db_press",
            datasetId = "0314",
            title = "Dumbbell Incline Bench Press",
            primaryMuscle = MuscleGroup.CHEST,
            secondaryMuscles = listOf(MuscleGroup.ARMS, MuscleGroup.SHOULDERS),
            equipment = EquipmentType.DUMBBELL,
            targetMuscle = "pectorals",
            bodyPart = "chest",
            imagePath = "images/0314-ns0SIbU.jpg",
            gifPath = "videos/0314-ns0SIbU.gif",
            summaryNotes = "Dataset #0314 • Body part: chest • Target: pectorals • Secondary: triceps, shoulders • Equipment: dumbbell.",
            instructions = listOf(
                "Set up an incline bench at a 45-degree angle.",
                "Sit on the bench with your feet flat on the ground and your back pressed firmly against the bench.",
                "Hold a dumbbell in each hand, palms facing forward, and lift them to shoulder height.",
                "Slowly lower the dumbbells to the sides of your chest, keeping your elbows at a 90-degree angle.",
                "Push the dumbbells back up to the starting position, fully extending your arms.",
                "Repeat for the desired number of repetitions."
            ),
            coachingCues = listOf(
                "Keep shoulder blades pinched together against the pad.",
                "Control the bottom stretch on every rep without bouncing.",
                "Press smoothly without clanging the dumbbells at lockout."
            )
        ),
        ExerciseDefinition(
            id = "ex_romanian_deadlift",
            datasetId = "0085",
            title = "Barbell Romanian Deadlift",
            primaryMuscle = MuscleGroup.LEGS,
            secondaryMuscles = listOf(MuscleGroup.LEGS, MuscleGroup.BACK),
            equipment = EquipmentType.BARBELL,
            targetMuscle = "glutes",
            bodyPart = "upper legs",
            imagePath = "images/0085-wQ2c4XD.jpg",
            gifPath = "videos/0085-wQ2c4XD.gif",
            summaryNotes = "Dataset #0085 • Body part: upper legs • Target: glutes • Secondary: hamstrings, lower back • Equipment: barbell.",
            instructions = listOf(
                "Stand with your feet shoulder-width apart and your toes pointing forward.",
                "Hold the barbell with an overhand grip, hands slightly wider than shoulder-width apart.",
                "Bend at the hips, keeping your back straight and your knees slightly bent.",
                "Lower the barbell towards the ground, keeping it close to your body.",
                "Feel the stretch in your hamstrings as you lower the barbell.",
                "Once you feel a stretch in your hamstrings, push your hips forward and stand up straight.",
                "Squeeze your glutes at the top of the movement.",
                "Lower the barbell back down to the starting position and repeat for the desired number of repetitions."
            ),
            coachingCues = listOf(
                "Think 'hips back and forward', not 'torso down and up'.",
                "Keep lats engaged so the barbell stays close to your thighs and shins.",
                "Maintain a neutral neck aligned with your spine."
            )
        ),
        ExerciseDefinition(
            id = "ex_cable_lateral_raise",
            datasetId = "0178",
            title = "Cable Lateral Raise",
            primaryMuscle = MuscleGroup.SHOULDERS,
            secondaryMuscles = listOf(MuscleGroup.BACK, MuscleGroup.ARMS),
            equipment = EquipmentType.CABLE,
            targetMuscle = "delts",
            bodyPart = "shoulders",
            imagePath = "images/0178-goJ6ezq.jpg",
            gifPath = "videos/0178-goJ6ezq.gif",
            summaryNotes = "Dataset #0178 • Body part: shoulders • Target: delts • Secondary: traps, triceps • Equipment: cable.",
            instructions = listOf(
                "Stand with your feet shoulder-width apart and grasp the cable handles with an overhand grip.",
                "Keep your arms straight and your core engaged.",
                "Raise your arms out to the sides until they are parallel to the floor.",
                "Pause for a moment at the top, then slowly lower your arms back down to the starting position.",
                "Repeat for the desired number of repetitions."
            ),
            coachingCues = listOf(
                "Lead with your elbows and avoid shrugging your upper traps.",
                "Raise in a comfortable scapular plane.",
                "Control the eccentric lowering against cable tension."
            )
        ),
        ExerciseDefinition(
            id = "ds_0294",
            datasetId = "0294",
            title = "Dumbbell Biceps Curl",
            primaryMuscle = MuscleGroup.ARMS,
            secondaryMuscles = listOf(MuscleGroup.ARMS),
            equipment = EquipmentType.DUMBBELL,
            targetMuscle = "biceps",
            bodyPart = "upper arms",
            imagePath = "images/0294-NbVPDMW.jpg",
            gifPath = "videos/0294-NbVPDMW.gif",
            summaryNotes = "Dataset #0294 • Body part: upper arms • Target: biceps • Secondary: forearms • Equipment: dumbbell.",
            instructions = listOf(
                "Stand up straight with a dumbbell in each hand, palms facing forward and arms fully extended.",
                "Keeping your upper arms stationary, exhale and curl the weights while contracting your biceps.",
                "Continue to raise the weights until your biceps are fully contracted and the dumbbells are at shoulder level.",
                "Hold the contracted position for a brief pause as you squeeze your biceps.",
                "Inhale and slowly begin to lower the dumbbells back to the starting position.",
                "Repeat for the desired number of repetitions."
            ),
            coachingCues = listOf(
                "Pin elbows by your sides and avoid swinging your torso.",
                "Control the lowering phase for full bicep tension."
            )
        ),
        ExerciseDefinition(
            id = "ds_0652",
            datasetId = "0652",
            title = "Pull-up",
            primaryMuscle = MuscleGroup.BACK,
            secondaryMuscles = listOf(MuscleGroup.ARMS),
            equipment = EquipmentType.BODYWEIGHT,
            targetMuscle = "lats",
            bodyPart = "back",
            imagePath = "images/0652-lBDjFxJ.jpg",
            gifPath = "videos/0652-lBDjFxJ.gif",
            summaryNotes = "Dataset #0652 • Body part: back • Target: lats • Secondary: biceps, forearms • Equipment: body weight.",
            instructions = listOf(
                "Hang from a pull-up bar with your palms facing away from you and your arms fully extended.",
                "Engage your core and squeeze your shoulder blades together.",
                "Pull your body up towards the bar by bending your elbows and bringing your chest towards the bar.",
                "Pause at the top of the movement, then slowly lower your body back down to the starting position.",
                "Repeat for the desired number of repetitions."
            ),
            coachingCues = listOf(
                "Initiate each pull by depressing your shoulder blades.",
                "Lower to a full dead hang between reps."
            )
        ),
        ExerciseDefinition(
            id = "ds_0334",
            datasetId = "0334",
            title = "Dumbbell Lateral Raise",
            primaryMuscle = MuscleGroup.SHOULDERS,
            secondaryMuscles = listOf(MuscleGroup.BACK),
            equipment = EquipmentType.DUMBBELL,
            targetMuscle = "delts",
            bodyPart = "shoulders",
            imagePath = "images/0334-DsgkuIt.jpg",
            gifPath = "videos/0334-DsgkuIt.gif",
            summaryNotes = "Dataset #0334 • Body part: shoulders • Target: delts • Secondary: traps • Equipment: dumbbell.",
            instructions = listOf(
                "Stand with your feet shoulder-width apart and hold a dumbbell in each hand, palms facing your body.",
                "Keep your back straight and engage your core.",
                "Raise your arms out to the sides until they are parallel to the floor, keeping a slight bend in your elbows.",
                "Pause for a moment at the top, then slowly lower your arms back down to the starting position.",
                "Repeat for the desired number of repetitions."
            ),
            coachingCues = listOf(
                "Keep a soft elbow bend and raise out to shoulder height.",
                "Avoid using hip momentum."
            )
        ),
        ExerciseDefinition(
            id = "ds_0001",
            datasetId = "0001",
            title = "3/4 Sit-up",
            primaryMuscle = MuscleGroup.CORE,
            secondaryMuscles = listOf(MuscleGroup.LEGS, MuscleGroup.BACK),
            equipment = EquipmentType.BODYWEIGHT,
            targetMuscle = "abs",
            bodyPart = "waist",
            imagePath = "images/0001-2gPfomN.jpg",
            gifPath = "videos/0001-2gPfomN.gif",
            summaryNotes = "Dataset #0001 • Body part: waist • Target: abs • Secondary: hip flexors, lower back • Equipment: body weight.",
            instructions = listOf(
                "Lie flat on your back with your knees bent and feet flat on the ground.",
                "Place your hands behind your head with your elbows pointing outwards.",
                "Engaging your abs, slowly lift your upper body off the ground, curling forward until your torso is at a 45-degree angle.",
                "Pause for a moment at the top, then slowly lower your upper body back down to the starting position.",
                "Repeat for the desired number of repetitions."
            ),
            coachingCues = listOf(
                "Avoid pulling on your neck with your hands.",
                "Exhale smoothly as you curl upward."
            )
        )
    )

    val defaultRoutines: List<RoutineTemplate> = listOf(
        RoutineTemplate(
            id = "rt_push_a",
            folderName = "",
            title = "Push Day A — Heavy Chest & Delts",
            notes = "Focus on progressive overload on Barbell Bench Press (RPE 8–9), followed by upper-chest and lateral delt hypertrophy.",
            exercises = listOf(
                RoutineTemplateExercise("ex_bench_press", "Barbell Bench Press", 4, 6, 100.0, 120),
                RoutineTemplateExercise("ex_overhead_press", "Barbell Seated Overhead Press", 3, 8, 60.0, 90),
                RoutineTemplateExercise("ex_incline_db_press", "Dumbbell Incline Bench Press", 3, 10, 34.0, 90),
                RoutineTemplateExercise("ex_cable_lateral_raise", "Cable Lateral Raise", 4, 15, 12.5, 60)
            )
        ),
        RoutineTemplate(
            id = "rt_pull_a",
            folderName = "",
            title = "Pull Day A — Posterior Chain & Lats",
            notes = "Deadlift top set followed by back-off volume and controlled vertical pulls.",
            exercises = listOf(
                RoutineTemplateExercise("ex_deadlift", "Barbell Deadlift", 3, 5, 160.0, 180),
                RoutineTemplateExercise("ex_lat_pulldown", "Cable Lat Pulldown Full Range Of Motion", 4, 10, 75.0, 90),
                RoutineTemplateExercise("ds_0294", "Dumbbell Biceps Curl", 3, 12, 16.0, 60)
            )
        ),
        RoutineTemplate(
            id = "rt_legs_a",
            folderName = "Push / Pull / Legs",
            title = "Legs Day A — Squat & Hamstring Hinge",
            notes = "Full-depth squats and controlled 3-second eccentric Barbell Romanian Deadlifts.",
            exercises = listOf(
                RoutineTemplateExercise("ex_back_squat", "Barbell Full Squat", 4, 5, 130.0, 150),
                RoutineTemplateExercise("ex_romanian_deadlift", "Barbell Romanian Deadlift", 3, 8, 110.0, 120)
            )
        ),
        RoutineTemplate(
            id = "rt_upper_power",
            folderName = "Upper / Lower Split",
            title = "Upper Body Strength & Volume",
            notes = "Antagonist superset structure pairing horizontal press and vertical pull.",
            exercises = listOf(
                RoutineTemplateExercise("ex_bench_press", "Barbell Bench Press", 4, 8, 95.0, 120),
                RoutineTemplateExercise("ex_lat_pulldown", "Cable Lat Pulldown Full Range Of Motion", 4, 10, 72.5, 90),
                RoutineTemplateExercise("ex_overhead_press", "Barbell Seated Overhead Press", 3, 8, 57.5, 90)
            )
        )
    )

    val defaultWorkouts: List<WorkoutSession> = listOf(
        WorkoutSession(
            id = "wk_2026_11_14",
            title = "Push Day A — Heavy Chest & Delts",
            startTimeEpochSeconds = 1763140000L,
            endTimeEpochSeconds = 1763143420L, // 57 min
            notes = "Strong bar speed on 102.5 kg bench press! Synced heart rate from Galaxy Watch7 Ultra.",
            averageHeartRateBpm = 136,
            caloriesBurnedKcal = 412.0,
            syncedToHealthConnect = false,
            exercises = listOf(
                WorkoutExerciseEntry(
                    id = "we_1",
                    exerciseId = "ex_bench_press",
                    exerciseTitle = "Barbell Bench Press",
                    notes = "Paused 1s on chest",
                    restTimerSeconds = 120,
                    sets = listOf(
                        WorkoutSetEntry("s_1", 1, SetType.WARMUP, "60kg × 10", 60.0, 10, 6.0, true),
                        WorkoutSetEntry("s_2", 2, SetType.NORMAL, "97.5kg × 6", 100.0, 6, 8.0, true),
                        WorkoutSetEntry("s_3", 3, SetType.NORMAL, "100kg × 6", 102.5, 6, 8.5, true),
                        WorkoutSetEntry("s_4", 4, SetType.FAILURE, "100kg × 5", 102.5, 5, 9.5, true)
                    )
                ),
                WorkoutExerciseEntry(
                    id = "we_2",
                    exerciseId = "ex_overhead_press",
                    exerciseTitle = "Barbell Seated Overhead Press",
                    notes = "Seated strict press",
                    restTimerSeconds = 90,
                    sets = listOf(
                        WorkoutSetEntry("s_5", 1, SetType.NORMAL, "60kg × 8", 60.0, 8, 8.0, true),
                        WorkoutSetEntry("s_6", 2, SetType.NORMAL, "60kg × 8", 62.5, 8, 8.5, true),
                        WorkoutSetEntry("s_7", 3, SetType.NORMAL, "60kg × 7", 62.5, 7, 9.0, true)
                    )
                ),
                WorkoutExerciseEntry(
                    id = "we_3",
                    exerciseId = "ex_incline_db_press",
                    exerciseTitle = "Dumbbell Incline Bench Press",
                    notes = "45° bench angle",
                    restTimerSeconds = 90,
                    sets = listOf(
                        WorkoutSetEntry("s_8", 1, SetType.NORMAL, "32kg × 10", 34.0, 10, 8.0, true),
                        WorkoutSetEntry("s_9", 2, SetType.NORMAL, "34kg × 10", 34.0, 10, 8.5, true),
                        WorkoutSetEntry("s_10", 3, SetType.DROPSET, "34kg × 8", 28.0, 12, 9.5, true)
                    )
                )
            )
        ),
        WorkoutSession(
            id = "wk_2026_11_12",
            title = "Legs Day A — Squat & Hamstring Hinge",
            startTimeEpochSeconds = 1762967200L,
            endTimeEpochSeconds = 1762970500L, // 55 min
            notes = "Deep high-bar squats and controlled RDLs.",
            averageHeartRateBpm = 144,
            caloriesBurnedKcal = 465.0,
            syncedToHealthConnect = false,
            exercises = listOf(
                WorkoutExerciseEntry(
                    id = "we_4",
                    exerciseId = "ex_back_squat",
                    exerciseTitle = "Barbell Full Squat",
                    notes = "High bar, Olympic shoes",
                    restTimerSeconds = 150,
                    sets = listOf(
                        WorkoutSetEntry("s_11", 1, SetType.WARMUP, "80kg × 8", 80.0, 8, 5.5, true),
                        WorkoutSetEntry("s_12", 2, SetType.NORMAL, "125kg × 5", 130.0, 5, 8.0, true),
                        WorkoutSetEntry("s_13", 3, SetType.NORMAL, "130kg × 5", 132.5, 5, 8.5, true),
                        WorkoutSetEntry("s_14", 4, SetType.NORMAL, "130kg × 5", 135.0, 5, 9.0, true)
                    )
                ),
                WorkoutExerciseEntry(
                    id = "we_5",
                    exerciseId = "ex_romanian_deadlift",
                    exerciseTitle = "Barbell Romanian Deadlift",
                    notes = "Straps on working sets",
                    restTimerSeconds = 120,
                    sets = listOf(
                        WorkoutSetEntry("s_15", 1, SetType.NORMAL, "105kg × 8", 110.0, 8, 8.0, true),
                        WorkoutSetEntry("s_16", 2, SetType.NORMAL, "110kg × 8", 115.0, 8, 8.5, true),
                        WorkoutSetEntry("s_17", 3, SetType.NORMAL, "110kg × 8", 115.0, 8, 9.0, true)
                    )
                )
            )
        ),
        WorkoutSession(
            id = "wk_2026_11_09",
            title = "Pull Day A — Posterior Chain & Lats",
            startTimeEpochSeconds = 1762708000L,
            endTimeEpochSeconds = 1762711120L, // 52 min
            notes = "Deadlift top set moved cleanly.",
            averageHeartRateBpm = 139,
            caloriesBurnedKcal = 398.0,
            syncedToHealthConnect = false,
            exercises = listOf(
                WorkoutExerciseEntry(
                    id = "we_6",
                    exerciseId = "ex_deadlift",
                    exerciseTitle = "Barbell Deadlift",
                    notes = "Chalk + hook grip",
                    restTimerSeconds = 180,
                    sets = listOf(
                        WorkoutSetEntry("s_18", 1, SetType.WARMUP, "100kg × 5", 100.0, 5, 6.0, true),
                        WorkoutSetEntry("s_19", 2, SetType.NORMAL, "155kg × 5", 160.0, 5, 8.5, true),
                        WorkoutSetEntry("s_20", 3, SetType.NORMAL, "160kg × 5", 165.0, 5, 9.0, true)
                    )
                ),
                WorkoutExerciseEntry(
                    id = "we_7",
                    exerciseId = "ex_lat_pulldown",
                    exerciseTitle = "Cable Lat Pulldown Full Range Of Motion",
                    notes = "1s pause at sternum",
                    restTimerSeconds = 90,
                    sets = listOf(
                        WorkoutSetEntry("s_21", 1, SetType.NORMAL, "72.5kg × 10", 75.0, 10, 8.0, true),
                        WorkoutSetEntry("s_22", 2, SetType.NORMAL, "75kg × 10", 77.5, 10, 8.5, true),
                        WorkoutSetEntry("s_23", 3, SetType.NORMAL, "75kg × 10", 77.5, 10, 9.0, true)
                    )
                )
            )
        ),
        WorkoutSession(
            id = "wk_2026_11_05",
            title = "Push Day A — Baseline Session",
            startTimeEpochSeconds = 1762362400L,
            endTimeEpochSeconds = 1762365600L,
            notes = "Week 1 baseline bench and overhead press.",
            averageHeartRateBpm = 131,
            caloriesBurnedKcal = 375.0,
            syncedToHealthConnect = false,
            exercises = listOf(
                WorkoutExerciseEntry(
                    id = "we_8",
                    exerciseId = "ex_bench_press",
                    exerciseTitle = "Barbell Bench Press",
                    restTimerSeconds = 120,
                    sets = listOf(
                        WorkoutSetEntry("s_24", 1, SetType.NORMAL, "95kg × 6", 95.0, 6, 7.5, true),
                        WorkoutSetEntry("s_25", 2, SetType.NORMAL, "95kg × 6", 97.5, 6, 8.0, true),
                        WorkoutSetEntry("s_26", 3, SetType.NORMAL, "97.5kg × 6", 97.5, 6, 8.5, true)
                    )
                ),
                WorkoutExerciseEntry(
                    id = "we_9",
                    exerciseId = "ex_overhead_press",
                    exerciseTitle = "Barbell Seated Overhead Press",
                    restTimerSeconds = 90,
                    sets = listOf(
                        WorkoutSetEntry("s_27", 1, SetType.NORMAL, "55kg × 8", 57.5, 8, 8.0, true),
                        WorkoutSetEntry("s_28", 2, SetType.NORMAL, "57.5kg × 8", 60.0, 8, 8.5, true)
                    )
                )
            )
        )
    )

    val defaultHealthConnectPermissions: List<HealthConnectPermissionItem> = listOf(
        HealthConnectPermissionItem(
            key = "read_bodyFat",
            androidPermission = "android.permission.health.READ_BODY_FAT",
            recordClassName = "BodyFatRecord",
            description = "Read body fat percentage measurements from Health Connect",
            granted = true
        ),
        HealthConnectPermissionItem(
            key = "read_weight",
            androidPermission = "android.permission.health.READ_WEIGHT",
            recordClassName = "WeightRecord",
            description = "Read body weight measurements (kg) from Health Connect",
            granted = true
        ),
        HealthConnectPermissionItem(
            key = "write_bodyFat",
            androidPermission = "android.permission.health.WRITE_BODY_FAT",
            recordClassName = "BodyFatRecord",
            description = "Write body fat percentage (replaces today's Lift Tracker entry)",
            granted = true
        ),
        HealthConnectPermissionItem(
            key = "write_weight",
            androidPermission = "android.permission.health.WRITE_WEIGHT",
            recordClassName = "WeightRecord",
            description = "Write body weight in kg (replaces today's Lift Tracker entry)",
            granted = true
        ),
        HealthConnectPermissionItem(
            key = "write_workout",
            androidPermission = "android.permission.health.WRITE_EXERCISE",
            recordClassName = "ExerciseSessionRecord",
            description = "Write completed strength training sessions",
            granted = true
        ),
        HealthConnectPermissionItem(
            key = "write_total_calories",
            androidPermission = "android.permission.health.WRITE_TOTAL_CALORIES_BURNED",
            recordClassName = "TotalCaloriesBurnedRecord",
            description = "Write active & total workout energy burned in kilocalories",
            granted = true
        ),
        HealthConnectPermissionItem(
            key = "write_heart_rate",
            androidPermission = "android.permission.health.WRITE_HEART_RATE",
            recordClassName = "HeartRateRecord",
            description = "Write workout heart rate samples streamed from the watch",
            granted = true
        )
    )

    val defaultBodyMeasurements: List<BodyMeasurementRecord> = listOf(
        BodyMeasurementRecord("bm_1", "write_weight", 82.4, "kg", "2026-11-15", 1763222400L, "Lift Tracker -> HealthConnect"),
        BodyMeasurementRecord("bm_2", "write_bodyFat", 14.2, "%", "2026-11-15", 1763222400L, "Lift Tracker -> HealthConnect"),
        BodyMeasurementRecord("bm_3", "read_weight", 82.8, "kg", "2026-11-10", 1762790400L, "HealthConnect ChangeToken Sync"),
        BodyMeasurementRecord("bm_4", "read_bodyFat", 14.5, "%", "2026-11-10", 1762790400L, "HealthConnect ChangeToken Sync")
    )
}
