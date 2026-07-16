package com.secondspine.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondspine.app.ui.theme.BodyStyle
import com.secondspine.app.ui.theme.ExportFooter
import com.secondspine.app.ui.theme.Ink
import com.secondspine.app.ui.theme.LabelStyle
import com.secondspine.app.ui.theme.MonoCaptionStyle
import com.secondspine.app.ui.theme.Paper
import com.secondspine.app.ui.theme.PaperDim
import com.secondspine.app.ui.theme.PaperFaint
import com.secondspine.app.ui.theme.RipFace
import com.secondspine.app.ui.theme.RipSpeech
import com.secondspine.app.ui.theme.SsIcons
import com.secondspine.app.ui.theme.SsPanel
import com.secondspine.app.ui.theme.SsSectionLabel
import com.secondspine.app.ui.theme.TapeWear
import com.secondspine.app.ui.theme.tapeGround
import com.secondspine.coach.Register
import com.secondspine.coach.RipLine
import com.secondspine.coach.Target

/**
 * THE GRACEFUL GOODBYE — a real uninstall flow, and the last thing this product does is the most
 * important thing it does.
 *
 * **An app you can leave with dignity is one you reinstall; an app that begs is one you delete with
 * prejudice.** That sentence is the whole design of this screen, and it is not sentiment — it is the
 * only retention mechanic available to a product whose thesis is that the coach removes himself. Rip
 * exists to make himself unnecessary. A version of him that fights the exit was never that character;
 * he was a subscription with a personality, and the user finds that out here, in the four seconds
 * that decide whether he ever comes back.
 *
 * **THE THINGS THAT ARE FORBIDDEN ON THIS SCREEN**, each of which is a standard pattern in the
 * category and each of which is a defect here:
 *
 *  - **No "are you sure?"** He is sure. He walked through Settings to get here.
 *  - **No retention offer.** No "how about we just dial it back?", no pause-instead-of-cancel, no
 *    reduced-intensity mode as a last-ditch save. The stand-down modes exist and are one screen back;
 *    dangling them *here* would convert them from a kindness into a trap.
 *  - **No guilt.** No "you were 12 days from graduating water." No progress he is abandoning. No
 *    summary of what he is throwing away. That is the begging, and it is the version of this screen
 *    that gets the app deleted with prejudice instead of merely deleted.
 *  - **No survey.** Nobody owes this app a reason on the way out.
 *  - **He says goodbye ONCE.** One line. He does not get a second beat, he does not get a callback,
 *    and he does not get to be funny about it. A man with no arms who cannot leave the tape, being
 *    dignified for four seconds, is the entire character paid off — and paying it off twice would
 *    make it a bit.
 *
 * **THE EXPORT COMES FIRST**, above his line, because the one thing that must not happen on the way
 * out is the user losing his archive. ~1,400 photographs of his own life are the asset this product
 * has claimed compounds since day one; letting them leave with him is how that claim stops being
 * marketing. It is offered, not required — an export gate on the exit would be the archive held
 * hostage by a hug.
 */
@Composable
fun GoodbyeScreen(
    state: SettingsState,
    onBack: () -> Unit,
    onExportNow: () -> Unit,
    onConfirmRetire: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Ink)
            .tapeGround(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()),
        ) {
            GoodbyeTopBar(onBack)

            Column(Modifier.padding(horizontal = 20.dp)) {

                // ---- 1. TAKE THE TAPE ----------------------------------------------------------
                // Flat, UI type, the app's own voice. This part is not his and he does not narrate
                // it: the archive was never his to give back, which is precisely what his line says
                // thirty pixels below.
                SsPanel(Modifier.fillMaxWidth(), wear = TapeWear.None) {
                    Column(Modifier.padding(20.dp)) {
                        SsSectionLabel("TAKE THE ARCHIVE WITH YOU")
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Your proofs are yours. Export them now and they are plain files in " +
                                "Documents/SecondSpine — readable by anything, needing nothing, " +
                                "outliving this app by design.",
                            style = BodyStyle,
                            color = PaperDim,
                        )
                        Spacer(Modifier.height(16.dp))
                        FlatButton("EXPORT NOW", onExportNow)
                        Spacer(Modifier.height(12.dp))
                        ExportFooter(state.daysSinceExport)
                        if (state.lastExportFileCount > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${state.lastExportFileCount} FILES WRITTEN",
                                style = MonoCaptionStyle,
                                color = PaperFaint,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(40.dp))

                // ---- 2. HE SAYS GOODBYE. ONCE. ------------------------------------------------
                RipFace(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    speaking = true,
                    // He is at the end. The face is drawn at the ending's weight regardless of what
                    // the odometer happens to say today, because this *is* the ending — the one the
                    // user chose rather than the one he earned, and Rip does not distinguish.
                    jurisdiction = 0,
                )
                Spacer(Modifier.height(20.dp))
                RipSpeech(
                    text = GOODBYE_LINE.text,
                    register = GOODBYE_LINE.register,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(40.dp))

                // ---- 3. THE DOOR ---------------------------------------------------------------
                // Paper on a hairline, not gold and not red. Gold would be him asking for the tap,
                // and he does not get to ask for this one. Red would make leaving an error.
                FlatButton("RETIRE HIM", onConfirmRetire)

                Spacer(Modifier.height(14.dp))
                Text(
                    "This stops every alarm, every demand and every notification, permanently. " +
                        "Your archive stays on your phone until you delete it yourself.",
                    style = MonoCaptionStyle,
                    color = PaperFaint,
                )

                Spacer(Modifier.height(56.dp))
            }
        }
    }
}

/**
 * THE LAST LINE. GHOST, and it is the only register it could be.
 *
 * GHOST is the wound and it is aimed at himself — `Tape.kt` says so where it explains why GHOST is
 * excluded from `MOCKING_REGISTERS`. It is also, structurally, the register in which the word
 * "brother" is unemittable: the word is the pitch, the pitch is the armour, and he does not have it
 * on any more. He never calls the user "brother" again, and the last thing the user notices about him
 * is something the user cannot quite name.
 *
 * Read what the line actually does:
 *
 *  - **"Take the tape. It was always yours"** — he gives up the only claim he ever had. Every
 *    aggressive thing he did for ten months was in service of an archive that was never his.
 *  - **"I just held the camera"** — the truest sentence in the product. He has no arms and no eyes;
 *    he was never the coach, he was the shutter. The user did all of it.
 *  - **"…It is going to be dark in here."** — the wound, stated once, flatly, with no ask attached.
 *  - **"Do not worry about it."** — and this is the entire character in five words. It is the same
 *    sentence he uses on the Comeback screen and on the COACH DOWN card, and it means the same thing
 *    every time: *my problem is not your problem.* He closes his own wound rather than handing it to
 *    the user as a reason to stay. That is the difference between dignity and begging, and it is one
 *    clause wide.
 */
internal val GOODBYE_LINE = RipLine(
    register = Register.GHOST,
    target = Target.himself,
    text = "Take the tape. It was always yours — I just held the camera. " +
        "…It is going to be dark in here. Do not worry about it.",
)

@Composable
private fun GoodbyeTopBar(onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            SsIcons.ChevronLeft,
            contentDescription = "Back",
            tint = PaperFaint,
            modifier = Modifier.size(22.dp).clickable(onClick = onBack),
        )
        Spacer(Modifier.width(14.dp))
        Text("RETIRE RIP", style = LabelStyle, color = Paper)
    }
}
