package pianoanchor.engine.content

import pianoanchor.core.model.ConceptCard

/**
 * The concept cards of the 32 course skills, in map order (`docs/course/beginner-course.md`). Each field is one plain
 * sentence for a total beginner and uses no term the course hasn't introduced by that concept. Text lives here, like
 * passage titles, and is translated with all content later. More songs and ear skills have no card.
 */
internal object ConceptCards {
    val all: List<ConceptCard> =
        listOf(
            ConceptCard(
                Skills.HAND_SHAPE.id,
                what =
                    "Sit tall, curve your hand like holding a ball, and number " +
                        "your fingers 1 (thumb) to 5 (little finger).",
                why = "Every later step tells you which finger to use, so you need the numbers first.",
                how = "A curved hand keeps each finger over its own key and ready to move.",
                whenMet = "Now, before any sound; the numbers come back in every lesson.",
            ),
            ConceptCard(
                Skills.MIDDLE_C.id,
                what = "Middle C is the C key nearest the middle of the piano.",
                why = "It is your home base, and every other key is found by how far it is from it.",
                how = "You will start almost every tune by finding it, and come back to it whenever you feel lost.",
                whenMet = "Now, as your very first sound; it glows so you can find it.",
            ),
            ConceptCard(
                Skills.BLACK_KEY_GROUPS.id,
                what = "The black keys come in groups of 2 and 3, alternating along the piano.",
                why =
                    "The groups let you find any key by touch without labels, " +
                        "so you can find middle C without a light.",
                how = "C sits just left of a group of 2 black keys, and F just left of a group of 3.",
                whenMet = "Now, as landmarks for finding keys; later they also play sharps and flats.",
            ),
            ConceptCard(
                Skills.HIGH_AND_LOW.id,
                what =
                    "Keys to the right sound higher and keys to the left sound lower, " +
                        "and the same pattern repeats along the piano.",
                why = "The pattern repeats every 7 white keys with the same names, so one landmark finds all of them.",
                how = "Use the group of 2 black keys to find every C, high or low, without looking at labels.",
                whenMet = "Now, right after the black keys, before any tune.",
            ),
            ConceptCard(
                Skills.STEADY_BEAT.id,
                what = "The beat is the steady pulse of music, like a ticking clock or your footsteps.",
                why = "Music needs a pulse, and later the app judges your timing against it.",
                how = "Play one note on each click; listen to the click and let it lead your hand.",
                whenMet = "Now, from the first beat lesson; timing is not judged yet.",
            ),
            ConceptCard(
                Skills.QUARTER_AND_HALF.id,
                what = "A note has a length: a quarter note lasts one click and a half note lasts two.",
                why = "Notes that are too short are the most common beginner slip, so length comes before tunes.",
                how = "Press on the click and keep the key down for the whole count before you let go.",
                whenMet = "Now, once you can keep a beat; whole notes come later.",
            ),
            ConceptCard(
                Skills.WHITE_KEY_NAMES.id,
                what = "The white keys are named C D E F G A B, then the names start again.",
                why = "The names hang on the black-key groups, so you can find any of them by feel.",
                how = "C D E are around the group of 2 and F G A B are around the group of 3.",
                whenMet = "Now, once you can find C; you will need the names to play tunes.",
            ),
            ConceptCard(
                Skills.RH_C_POSITION.id,
                what = "In the right-hand C position, thumb rests on middle C and fingers 1 to 5 sit on C D E F G.",
                why = "One finger per key means you can play without looking down or moving your hand.",
                how = "Keep the hand still and press each note with its own finger, up from C to G and back.",
                whenMet = "Now, as soon as you know the key names; all of Unit 1 uses it.",
            ),
            ConceptCard(
                Skills.STEPS_AND_SKIPS.id,
                what = "A step goes to the next key; a skip jumps over one key.",
                why = "Tunes move by steps and skips, and later you will read music by how far notes move.",
                how = "From C, a step up is D and a skip up is E.",
                whenMet = "Now, with your hand in position; it also helps you read music later.",
            ),
            ConceptCard(
                Skills.FINGER_NUMBER_TUNES.id,
                what = "A first tune is a real song you play from finger numbers alone.",
                why = "It is a whole song made only of what you already know, so real music starts on day one.",
                how = "Read the finger numbers, press the key each one points to, and keep to the beat.",
                whenMet = "Now, after steps and skips; no music reading needed yet.",
            ),
            ConceptCard(
                Skills.BARS_AND_COUNTING.id,
                what = "Bar lines cut music into groups of beats, and here each bar has four beats, counted 1 2 3 4.",
                why = "Counting to 4 tells you where you are in a bar, and beat 1 is the one you lean on a little.",
                how = "Count out loud 1 2 3 4, press a little harder on 1, and treat each bar line as a fresh start.",
                whenMet = "Now, after your first tunes; it also prepares you for reading music.",
            ),
            ConceptCard(
                Skills.STAFF_STEPS.id,
                what = "The staff is five lines on which notes sit, and higher on the page means higher on the piano.",
                why = "It is how music is written, and middle C is where you start reading it.",
                how = "A step moves from a line to the next space, or from a space to the next line.",
                whenMet = "Now, after counting; you start with just a few notes.",
            ),
            ConceptCard(
                Skills.TREBLE_CLEF.id,
                what =
                    "The treble clef is the curly sign at the start of the top " +
                        "staff; its curl circles the line for G.",
                why =
                    "A second landmark means you can find a note from a nearby " +
                        "one instead of counting from the bottom.",
                how = "A skip goes from a line to the next line, or from a space to the next space.",
                whenMet = "Now, once steps on the staff feel easy; it shows where the right hand plays.",
            ),
            ConceptCard(
                Skills.SOFT_AND_LOUD.id,
                what = "Soft is marked p and loud is marked f, and it is how hard you press the key.",
                why =
                    "Playing softer or louder is what makes a tune sound alive, " +
                        "and the piano can feel how hard you play.",
                how = "Press gently for p and a bit more firmly for f, with a relaxed arm.",
                whenMet = "Now, with tunes you can already play; the app does not grade loudness yet.",
            ),
            ConceptCard(
                Skills.LH_C_POSITION.id,
                what = "In the left-hand C position, fingers 5 to 1 sit on the C below middle C up to G.",
                why = "The left hand mirrors the right, but its finger numbers run the other way.",
                how = "Put your little finger, number 5, on the C below middle C and your thumb on G.",
                whenMet = "Now, after the right hand is steady; you will use it for bass notes and chords.",
            ),
            ConceptCard(
                Skills.BASS_CLEF_WHOLE_NOTE.id,
                what =
                    "The bass clef is the sign at the start of the lower staff, " +
                        "and its two dots surround the line for F.",
                why = "The left hand reads from the lower staff, and F is its landmark.",
                how =
                    "Find F between the two dots, and hold each whole note, an open oval with no stem, " +
                        "for four clicks.",
                whenMet = "Now, with the left hand ready; the left hand mostly holds long notes.",
            ),
            ConceptCard(
                Skills.GRAND_STAFF_TURNS_RESTS.id,
                what = "The grand staff joins two staves, and a rest is silence that you still count.",
                why = "Real piano music is written on both staves together, and silence is part of the rhythm.",
                how = "Play when your hand has notes, wait and count when it has a rest, then take your turn.",
                whenMet = "Now, with both clefs known; hands take turns before they play together.",
            ),
            ConceptCard(
                Skills.THREE_FOUR_TIME.id,
                what = "In 3/4 time each bar has three beats, counted 1 2 3, like the lilt of a waltz.",
                why = "Music can count in 3 as well as in 4, and dances like the waltz feel this way.",
                how = "Count 1 2 3, and hold a dotted half note, which lasts the whole three beats.",
                whenMet = "Now, once counting in 4 is easy; it is the course's second time signature.",
            ),
            ConceptCard(
                Skills.HANDS_TOGETHER_HELD.id,
                what = "Hands together means the left hand holds one note down while the right hand plays the tune.",
                why = "It is the first step to playing two parts at once, and your hands do different jobs.",
                how = "Press the left note, keep it down, and play the tune with the right hand over it.",
                whenMet = "Now, after hands have taken turns; chords come next.",
            ),
            ConceptCard(
                Skills.C_CHORD.id,
                what = "A chord is notes played together; the C chord is C, E and G.",
                why = "A chord backs a tune with a fuller sound, and it is how the left hand will accompany you.",
                how = "Play fingers 1, 3 and 5 on C, E and G at the same moment, as one sound.",
                whenMet = "Now, once your left hand can hold notes; G and F chords come next.",
            ),
            ConceptCard(
                Skills.G_AND_F_CHORDS.id,
                what =
                    "G and F chords sit near C, and chord symbols are the letters " +
                        "above a tune that name which one to play.",
                why = "Those chords need the fewest finger moves between them, so you can change smoothly.",
                how = "Read the letter above the music, play that chord and change on beat 1 of the bar.",
                whenMet = "Now, with the C chord known; most simple songs need only these three.",
            ),
            ConceptCard(
                Skills.MELODY_AND_CHORDS.id,
                what = "Melody plus chords is the tune in your right hand over chords in your left.",
                why = "It is the goal of the course: a real song that sounds like a full piece.",
                how = "Play the left chord on the first beat of each bar and keep the tune going on top.",
                whenMet = "Now, when the three chords are comfortable; the course's biggest songs use it.",
            ),
            ConceptCard(
                Skills.SHARPS_AND_FLATS.id,
                what = "A sharp is the black key just above a white key, and a flat is the black key just below it.",
                why =
                    "The black keys' second job: they fill in the steps between " +
                        "white keys and give songs other sounds.",
                how = "A sharp (♯) means play the black key to the right, and a flat (♭) the black key to the left.",
                whenMet = "Now, to finish the course and close the loop on the black keys; later songs use them.",
            ),
            ConceptCard(
                Skills.EIGHTH_NOTES.id,
                what = "Eighth notes are two even notes in one click, counted \"1 and\".",
                why = "Music moves faster than the beat; splitting the click in two keeps those notes even.",
                how = "Count \"1 & 2 & 3 & 4 &\" and play on every word.",
                whenMet = "Now, and in most songs from here on.",
            ),
            ConceptCard(
                Skills.EIGHTH_PAIRS.id,
                what = "Eighths are joined by a beam in pairs, one pair per click.",
                why = "The beam shows at a glance which notes share a click.",
                how = "Read a beamed pair as one click split in two, and quarters as one click each.",
                whenMet = "Now, mixing quarters and eighths in tunes.",
            ),
            ConceptCard(
                Skills.DOTTED_QUARTER.id,
                what = "A dot adds half a note's length, so a dotted quarter lasts one and a half clicks.",
                why = "It makes the long-short swing in many songs.",
                how = "Count \"1 (2) &\": hold through 2, play the eighth on \"and\".",
                whenMet = "Now; Jingle Bells and Ode to Joy use it.",
            ),
            ConceptCard(
                Skills.RHYTHM_SONG.id,
                what = "The Jingle Bells chorus with left-hand chords.",
                why = "It puts eighths and the dotted quarter together in music you know.",
                how = "Play the tune with the right hand while the left hand holds C, F and G chords.",
                whenMet = "Now, to finish the unit.",
            ),
            ConceptCard(
                Skills.RH_G_POSITION.id,
                what = "Right-hand G position puts the thumb on the G above middle C, one finger per key up to D.",
                why = "A new home lets you play tunes that sit higher without moving your hand.",
                how = "Fingers 1 to 5 on G A B C D.",
                whenMet = "Now; many songs in G sit here.",
            ),
            ConceptCard(
                Skills.LH_G_POSITION.id,
                what = "Left-hand G position puts the little finger on the low G, one finger per key up to D.",
                why = "The left hand moves to G with the right, so both hands can play in G.",
                how = "Fingers 5 to 1 on G A B C D, low on the bass staff.",
                whenMet = "Now, for chords and bass notes in G.",
            ),
            ConceptCard(
                Skills.KEY_OF_G.id,
                what = "A key is the set of notes a song uses; the key of G uses F\u266F instead of F.",
                why = "A scale starting on G needs F\u266F to keep the same pattern of steps as C.",
                how = "The sharp on the F line at the start of each staff means play every F as F\u266F.",
                whenMet = "Now; songs in G show one sharp.",
            ),
            ConceptCard(
                Skills.G_AND_D7_CHORDS.id,
                what = "The two main chords in G are G (G B D) and D7, played as F\u266F C D by the left hand.",
                why = "D7 pulls back home to G, and this short form moves only two fingers.",
                how = "From G B D, the little finger slides down to F\u266F and finger 2 takes C.",
                whenMet = "Now; most songs in G use these two chords.",
            ),
            ConceptCard(
                Skills.MELODY_CHORDS_IN_G.id,
                what = "Go Tell Aunt Rhody with G and D7 chords.",
                why = "It puts the new key, both hand positions and the chords together.",
                how = "Right hand in G position, left hand changes chord on beat 1.",
                whenMet = "Now, to finish the unit.",
            ),
        )
}
