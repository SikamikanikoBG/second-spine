package com.secondspine.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondspine.app.ui.theme.BodyStyle
import com.secondspine.app.ui.theme.Paper
import com.secondspine.app.ui.theme.PaperDim
import com.secondspine.app.ui.theme.SsPanel
import com.secondspine.app.ui.theme.SsSectionLabel
import com.secondspine.app.ui.theme.TapeWear

/**
 * THE ONE BREAK.
 *
 * The character breaks exactly once in this product, here, for this, and never anywhere else. That
 * scarcity is not a flourish — it is the mechanism. A man who never steps out from behind the bit is
 * a bit; a man who steps out **once**, for the safety explanation, and then never again, is a man
 * with a line he will not cross. The other 100% of his aggression is trustworthy *because* of this
 * four seconds, and it stops being trustworthy the moment there is a second break.
 *
 * So the rules on this composable are absolute:
 *
 *  - **UI type, not display type.** `BodyStyle` is `SsFonts.Ui` — the font of the *app*, the font of
 *    every label and affordance. `Type.kt` splits the families by *who is speaking*, and the switch
 *    is the tell: the typeface itself announces that he has stopped performing, before a word of it
 *    is read.
 *  - **[RipSpeech] is deliberately not used.** That is the single most important line in this file.
 *    The component that renders Rip assigns a register, paints him gold, and puts him on tape. Here
 *    he gets none of the three. The one place in the app where the character speaks and the
 *    character's own component is not called is the one place the character is not performing.
 *  - **No gold.** Gold means he is talking, in the sense of *selling*. He is not selling.
 *  - **No tape wear. No grain, no scanlines, no tracking.** [TapeWear.None]. The medium stops
 *    interfering, which reads as the man stepping out from behind it. Same reasoning as the
 *    DISAPPOINTED register's total absence of distortion in `Components.kt`.
 *  - **Flat delivery. Four seconds. No jokes.** Not a soft joke, not a warm callback at the end, not
 *    a wink on the way out. A joke here would retract the entire thing and would cost more than every
 *    joke in the fragment bank earns.
 *
 * **On the copy.** It never says "are you okay", never asks a question, and never implies the reader
 * is in crisis for having opened Settings. It states a mechanism and a guarantee, because that is
 * what the reader actually needs and because being asked "are you okay?" by a fitness app is its own
 * small violence. The last sentence is the promise the rest of the product then keeps, and keeping it
 * is what makes it worth having made.
 */
@Composable
fun SafetyBreak(modifier: Modifier = Modifier) {
    SsPanel(modifier.fillMaxWidth(), wear = TapeWear.None) {
        Column(Modifier.padding(20.dp)) {
            // The app's own label, not his. He does not get a title card for this.
            SsSectionLabel("BREAK GLASS")
            Spacer(Modifier.height(14.dp))

            Text(
                text = "Okay. This part is not a bit.",
                style = BodyStyle,
                color = Paper,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Break glass is the grey button. One tap and whatever I am doing stops — the " +
                    "alarm, the lock, the demand. It works every time. There is no confirmation and " +
                    "there is no delay.",
                style = BodyStyle,
                color = PaperDim,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "I do not count it. I do not score it. It is not in the Ledger, it is not on " +
                    "the Tape, and it is not in any number I can reach. I will not bring it up on " +
                    "Sunday. I will not bring it up ever. Nothing follows it.",
                style = BodyStyle,
                color = PaperDim,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "You do not owe me a reason. Pain is a reason. No reason is a reason.",
                style = BodyStyle,
                color = PaperDim,
            )
            Spacer(Modifier.height(12.dp))
            // The promise. Everything the character does for the next ten months is underwritten by
            // this sentence being true, and it is only true because this is the only time it is said.
            Text(
                text = "That is the only time I will talk to you like this.",
                style = BodyStyle,
                color = Paper,
            )
        }
    }
}
