package tv.livo.sdk.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tv.livo.sdk.LivoHosts
import tv.livo.sdk.community.CommunityMode
import tv.livo.sdk.community.CommunityTarget
import tv.livo.sdk.community.CommunityTargetKind
import tv.livo.sdk.community.LivoCommunity
import tv.livo.sdk.player.LivoPlayer
import tv.livo.sdk.studio.LivoGuestStudio
import tv.livo.sdk.studio.LivoHostStudio

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var tab by remember { mutableIntStateOf(0) }
            var token by remember { mutableStateOf("") }
            var mediaId by remember { mutableStateOf("") }
            Column(Modifier.padding(12.dp)) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Player") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Studio") })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Community") })
                }
                OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Token / id") })
                when (tab) {
                    0 -> {
                        OutlinedTextField(value = mediaId, onValueChange = { mediaId = it }, label = { Text("Stream or VOD id") })
                        if (mediaId.isNotBlank()) {
                            LivoPlayer(streamId = mediaId, hosts = LivoHosts.production)
                        }
                    }
                    1 -> {
                        if (token.startsWith("host:")) {
                            LivoHostStudio(hostToken = token.removePrefix("host:"), hosts = LivoHosts.production)
                        } else if (token.isNotBlank()) {
                            LivoGuestStudio(guestToken = token, hosts = LivoHosts.production)
                        }
                    }
                    else ->
                        if (mediaId.isNotBlank()) {
                            LivoCommunity(
                                target = CommunityTarget(CommunityTargetKind.STREAM, mediaId),
                                mode = CommunityMode.VIEWER,
                                hosts = LivoHosts.production,
                            )
                        }
                }
            }
        }
    }
}
