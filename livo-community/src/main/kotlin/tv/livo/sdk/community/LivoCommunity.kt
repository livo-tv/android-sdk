package tv.livo.sdk.community

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tv.livo.sdk.LivoApiClient
import tv.livo.sdk.LivoApiException
import tv.livo.sdk.LivoCredentials
import tv.livo.sdk.LivoHosts
import tv.livo.sdk.models.CommunityIdentity
import tv.livo.sdk.models.CommunityItem
import tv.livo.sdk.models.CommunityType
import java.util.UUID

public enum class CommunityTargetKind { STREAM, VOD }

public enum class CommunityMode { VIEWER, CONTROL }

public data class CommunityTarget(val kind: CommunityTargetKind, val id: String) {
    public val path: String
        get() =
            when (kind) {
                CommunityTargetKind.STREAM -> "streams"
                CommunityTargetKind.VOD -> "vods"
            }
}

private const val IDENTITY_PREFS = "livo-community-identity"

@Composable
public fun LivoCommunity(
    target: CommunityTarget,
    mode: CommunityMode,
    hosts: LivoHosts,
    credentials: LivoCredentials = LivoCredentials.None,
    identity: CommunityIdentity? = null,
    modifier: Modifier = Modifier,
    showQa: Boolean = target.kind == CommunityTargetKind.STREAM,
) {
    val context = LocalContext.current
    val client = remember(hosts, credentials) { LivoApiClient(hosts, credentials) }
    val resolved = identity ?: loadIdentity(context)
    var tab by remember { mutableStateOf(CommunityType.COMMENT) }
    var items by remember { mutableStateOf<List<CommunityItem>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var closed by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(target, tab) {
        while (isActive) {
            val page =
                runCatching {
                    if (mode == CommunityMode.CONTROL) {
                        client.streams.takeIf { target.kind == CommunityTargetKind.STREAM }
                        client.let {
                            when (target.kind) {
                                CommunityTargetKind.STREAM -> client.streams.community(target.id).list(tab)
                                CommunityTargetKind.VOD -> client.vods.community(target.id).list(tab)
                            }
                        }
                    } else {
                        client.public.community(target.path, target.id).list(tab)
                    }
                }.getOrNull()
            if (page != null) items = page.items
            delay(5_000)
        }
    }

    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == CommunityType.COMMENT, onClick = { tab = CommunityType.COMMENT }, label = { Text("Comments") })
            if (showQa) {
                FilterChip(selected = tab == CommunityType.QUESTION, onClick = { tab = CommunityType.QUESTION }, label = { Text("Q&A") })
            }
        }
        closed?.let { Text(it) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f, fill = false)) {
            items(items, key = { it.id }) { item ->
                CommunityRow(
                    item = item,
                    mode = mode,
                    onUpvote = {
                        scope.launch {
                            val public = client.public.community(target.path, target.id)
                            if (mode == CommunityMode.VIEWER) {
                                runCatching { public.canAnswer(resolved.guestId) }
                            }
                            runCatching { public.upvote(item.id, resolved.guestId) }
                        }
                    },
                    onAnswer = { body ->
                        scope.launch {
                            runCatching {
                                if (mode == CommunityMode.CONTROL && target.kind == CommunityTargetKind.STREAM) {
                                    client.streams.community(target.id)
                                        .answer(item.id, body, resolved.displayName, resolved.guestId, resolved.picture)
                                } else {
                                    client.public.community(target.path, target.id)
                                        .answer(item.id, body, resolved.displayName, resolved.guestId, resolved.picture)
                                }
                            }
                        }
                    },
                )
            }
        }
        OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = {
            val body = draft.trim()
            if (body.isEmpty()) return@TextButton
            scope.launch {
                val payload =
                    buildJsonObject {
                        put("type", if (tab == CommunityType.QUESTION) "question" else "comment")
                        put("body", body)
                        put("displayName", resolved.displayName)
                        put("guestId", resolved.guestId)
                    }
                runCatching {
                    if (mode == CommunityMode.CONTROL) {
                        when (target.kind) {
                            CommunityTargetKind.STREAM -> client.streams.community(target.id).create(payload)
                            CommunityTargetKind.VOD -> client.vods.community(target.id).create(payload)
                        }
                    } else {
                        client.public.community(target.path, target.id).create(payload)
                    }
                }.onFailure { err ->
                    val code = (err as? LivoApiException)?.code
                    closed =
                        when (code) {
                            LivoApiException.COMMENTS_CLOSED -> "Comments are closed"
                            LivoApiException.QA_CLOSED -> "Q&A is closed"
                            else -> err.message
                        }
                }
                draft = ""
            }
        }) { Text("Send") }
    }
}

@Composable
private fun CommunityRow(item: CommunityItem, mode: CommunityMode, onUpvote: () -> Unit, onAnswer: (String) -> Unit) {
    var answer by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        Text(item.displayName ?: "Guest")
        Text(item.body)
        TextButton(onClick = onUpvote) { Text("▲ ${item.upvoteCount}") }
        item.replies.orEmpty().forEach { reply ->
            Text(reply.body, modifier = Modifier.padding(start = 16.dp))
        }
        if (mode == CommunityMode.CONTROL && item.type == CommunityType.QUESTION && item.answer == null) {
            OutlinedTextField(value = answer, onValueChange = { answer = it })
            TextButton(onClick = { onAnswer(answer) }) { Text("Answer") }
        }
        item.answer?.let { Text("A: ${it.body}") }
    }
}

public fun loadIdentity(context: Context): CommunityIdentity {
    val prefs = context.getSharedPreferences(IDENTITY_PREFS, Context.MODE_PRIVATE)
    val guestId = prefs.getString("guestId", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("guestId", it).apply()
    }
    val name = prefs.getString("displayName", null) ?: "Guest"
    return CommunityIdentity(displayName = name, guestId = guestId)
}

public fun saveIdentity(context: Context, identity: CommunityIdentity) {
    context.getSharedPreferences(IDENTITY_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString("guestId", identity.guestId)
        .putString("displayName", identity.displayName)
        .apply()
}
