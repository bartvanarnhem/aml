package com.autominimal.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * First-run screen: says what each permission is for. "Continue" walks through the missing ones
 * one after another; each row can also be granted on its own, and everything can be skipped.
 */
@Composable
fun SetupScreen(
    permissions: PermissionStatus,
    onContinue: () -> Unit,
    onAccess: (Access) -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(C.Ground).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 28.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Let your home screen sort itself", fontSize = 26.sp, fontWeight = FontWeight.Medium, lineHeight = 32.sp)
            Text(
                "Your lists fill up with the apps you use most and the people you're most in touch with. " +
                    "For that, the launcher needs to see a few things.",
                fontSize = 15.sp, color = C.Sub, lineHeight = 21.sp,
            )
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SymbolIcon(Sym.Lock, 18.dp, C.Green)
                Text("Everything stays on this phone. Nothing is uploaded.", fontSize = 14.sp, color = C.Green)
            }
            Column {
                Access.entries.forEach { access ->
                    val granted = permissions.granted(access)
                    Column(Modifier.clickable(enabled = !granted, role = Role.Button, onClickLabel = "Allow ${access.title}") { onAccess(access) }) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Hair))
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(Modifier.size(36.dp).background(C.Card, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                SymbolIcon(access.icon, 20.dp, C.Soft)
                            }
                            TitleAndHint(access.title, access.hint, Modifier.weight(1f), size = 15.sp, gap = 1.dp)
                            if (granted) SymbolIcon(Sym.CheckCircle, 22.dp, C.Green, filled = true, description = "Allowed")
                            else Text("Allow", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = C.Accent)
                        }
                    }
                }
            }
            Text(
                "All of these are optional. You can change them any time under Settings › Privacy.",
                fontSize = 13.sp, color = C.Muted, modifier = Modifier.padding(top = 6.dp),
            )
        }
        Column(Modifier.padding(top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (permissions.allGranted) {
                AccentButton("Done", onDone, Modifier.fillMaxWidth())
            } else {
                AccentButton("Continue", onContinue, Modifier.fillMaxWidth())
                Text(
                    "Skip for now", fontSize = 14.sp, color = C.Muted,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onDone).padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}
