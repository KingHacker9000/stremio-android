#!/usr/bin/env python3
"""Apply the Stremio Sense Android MVP to stremio-native/stremio-android."""
from __future__ import annotations
import argparse
from pathlib import Path
import shutil

HERE = Path(__file__).resolve().parent

def copy_file(src: Path, dst: Path) -> None:
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)

def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"upstream anchor changed: {label}")
    return text.replace(old, new, 1)

def patch(path: Path, fn) -> None:
    old = path.read_text(encoding="utf-8")
    new = fn(old)
    if new != old:
        path.write_text(new, encoding="utf-8")
        print(f"patched {path}")

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("checkout", type=Path)
    args = ap.parse_args()
    root = args.checkout.resolve()
    app = root / "app/src/main/java/com/stremio/mobile"
    if not (app / "presentation/screens/StremioMobileApp.kt").exists():
        raise SystemExit("not a supported stremio-native/stremio-android checkout")

    files = {
        "SenseIndex.kt": app / "sense/SenseIndex.kt",
        "SenseRepository.kt": app / "sense/SenseRepository.kt",
        "SenseAndroidDownloads.kt": app / "sense/SenseAndroidDownloads.kt",
        "SenseDownloadsPanel.kt": app / "presentation/screens/SenseDownloadsPanel.kt",
        "SenseTopPicksRow.kt": app / "presentation/screens/SenseTopPicksRow.kt",
    }
    for src, dst in files.items():
        copy_file(HERE / src, dst)

    section = app / "presentation/state/MainSection.kt"
    patch(section, lambda s: replace_once(s, "    Library,\n    Settings,", "    Library,\n    Downloads,\n    Settings,", "MainSection Downloads"))

    nav = app / "presentation/navigation/AppView.kt"
    def patch_nav(s: str) -> str:
        s = replace_once(s, "import androidx.compose.material.icons.outlined.Explore\n", "import androidx.compose.material.icons.outlined.Download\nimport androidx.compose.material.icons.outlined.Explore\n", "Downloads icon import")
        s = replace_once(s, "    Library(\"Library\", Icons.Outlined.VideoLibrary),\n    Settings", "    Library(\"Library\", Icons.Outlined.VideoLibrary),\n    Downloads(\"Downloads\", Icons.Outlined.Download),\n    Settings", "Downloads AppView")
        s = replace_once(s, "    AppView.Library -> MainSection.Library\n    AppView.Settings", "    AppView.Library -> MainSection.Library\n    AppView.Downloads -> MainSection.Downloads\n    AppView.Settings", "AppView to section")
        s = replace_once(s, "    MainSection.Library -> AppView.Library\n    MainSection.Settings", "    MainSection.Library -> AppView.Library\n    MainSection.Downloads -> AppView.Downloads\n    MainSection.Settings", "section to AppView")
        return s
    patch(nav, patch_nav)

    streams = app / "presentation/screens/StreamsSheet.kt"
    def patch_streams(s: str) -> str:
        s = replace_once(s, "import androidx.compose.material.icons.outlined.Cloud\n", "import androidx.compose.material.icons.outlined.Cloud\nimport androidx.compose.material.icons.outlined.Download\n", "Streams download icon")
        s = replace_once(s, "    onSelect: (StreamOption) -> Unit,\n    onSelectEpisode", "    onSelect: (StreamOption) -> Unit,\n    onDownload: (StreamOption) -> Unit,\n    onSelectEpisode", "StreamsSheet onDownload")
        s = replace_once(s, "onSelect = { onSelect(option) }\n", "onSelect = { onSelect(option) },\n                                onDownload = { onDownload(option) }\n", "StreamRow call download")
        s = replace_once(s, "    enabled: Boolean,\n    onSelect: () -> Unit,\n) {", "    enabled: Boolean,\n    onSelect: () -> Unit,\n    onDownload: () -> Unit,\n) {", "StreamRow signature")
        marker = """            }\n        }\n    }\n}\n}\n\n@Composable\nprivate fun FilterChip(\n"""
        injection = """            }\n        }\n        Spacer(modifier = Modifier.width(8.dp))\n        ThemedIconButton(\n            imageVector = Icons.Outlined.Download,\n            contentDescription = \"Download\",\n            onClick = onDownload,\n            modifier = Modifier.size(40.dp),\n            containerColor = GlassSurface,\n        )\n    }\n}\n}\n\n@Composable\nprivate fun FilterChip(\n"""
        return replace_once(s, marker, injection, "StreamRow download button")
    patch(streams, patch_streams)

    vm = app / "presentation/viewmodel/MainViewModel.kt"
    def patch_vm(s: str) -> str:
        # Use existing broad import section as stable anchor rather than a specific server import.
        if "import com.stremio.mobile.sense.SenseAndroidDownloads" not in s:
            anchor = "import com.stremio.mobile"
            pos = s.find(anchor)
            if pos < 0:
                raise SystemExit("upstream anchor changed: VM imports")
            line_end = s.find("\n", pos)
            while line_end >= 0:
                next_start = line_end + 1
                if not s.startswith("import com.stremio.mobile", next_start):
                    break
                line_end = s.find("\n", next_start)
            s = s[:line_end+1] + "import com.stremio.mobile.sense.SenseAndroidDownloads\nimport com.stremio.mobile.sense.SenseRepository\n" + s[line_end+1:]
        # Derive application Context from existing AndroidViewModel application.
        class_anchor = "class MainViewModel("
        if class_anchor not in s:
            raise SystemExit("upstream anchor changed: MainViewModel class")
        # Prefer an existing appContext property if present.
        if "private val senseDownloads" not in s:
            property_anchor = "    private val appContext = appContext.applicationContext\n"
            if property_anchor in s:
                s = s.replace(property_anchor, property_anchor + "    private val senseDownloads = SenseAndroidDownloads(this.appContext)\n    private val senseRepository = SenseRepository(this.appContext)\n", 1)
            else:
                # Current client constructor exposes appContext as parameter; add after first private property block.
                marker = "    private val authRepository"
                idx = s.find(marker)
                if idx < 0:
                    raise SystemExit("upstream anchor changed: Sense VM properties")
                s = s[:idx] + "    private val senseDownloads = SenseAndroidDownloads(appContext)\n    private val senseRepository = SenseRepository(appContext)\n" + s[idx:]
        method = """    fun downloadStream(option: StreamOption) {\n        viewModelScope.launch {\n            runCatching {\n                startServerInternal()\n                val url = runCatching { core.resolvePlayableUrl(option.core).first() }.getOrNull()\n                    ?: core.directUrl(option.core.stream)\n                    ?: error(\"Could not resolve a downloadable URL\")\n                val current = streams.value\n                val item = current.forItem\n                val title = listOfNotNull(item?.name, option.quality).filter { it.isNotBlank() }.joinToString(\" - \")\n                senseDownloads.enqueue(\n                    sourceUrl = url,\n                    name = title.ifBlank { option.name },\n                    contentId = item?.id,\n                    videoId = current.selectedVideoId,\n                )\n            }.onFailure { Timber.w(it, \"Failed to enqueue offline download\") }\n        }\n    }\n\n"""
        if method not in s:
            close_anchor = "    fun closePlayer() {\n"
            if close_anchor not in s: raise SystemExit("upstream anchor changed: MainViewModel closePlayer")
            s = s.replace(close_anchor, method + close_anchor, 1)
        old_end = """    fun onPlaybackEnded() {\n        playbackRepository.reportEnded()\n"""
        new_end = """    fun onPlaybackEnded() {\n        streams.value.forItem?.id?.let { senseRepository.record(it, \"completed\") }\n        playbackRepository.reportEnded()\n"""
        s = replace_once(s, old_end, new_end, "Android playback history")
        return s
    patch(vm, patch_vm)

    mobile = app / "presentation/screens/StremioMobileApp.kt"
    def patch_mobile(s: str) -> str:
        s = replace_once(s, "                    onSelect = viewModel::playStream,\n", "                    onSelect = viewModel::playStream,\n                    onDownload = viewModel::downloadStream,\n", "StreamsSheet download callback")
        home_anchor = """                    if (state.continueWatching.items.isNotEmpty() || state.continueWatching.isLoading) {\n"""
        home_insert = """                    item(contentType = \"sense-top-picks\") {\n                        SenseTopPicksRow(\n                            seedIds = state.continueWatching.items.map { it.id },\n                            onOpenDetails = onOpenDetails,\n                        )\n                    }\n                    if (state.continueWatching.items.isNotEmpty() || state.continueWatching.isLoading) {\n"""
        s = replace_once(s, home_anchor, home_insert, "Android Top Picks")
        settings_anchor = """                MainSection.Settings -> {\n"""
        downloads_branch = """                MainSection.Downloads -> {\n                    item { SenseDownloadsPanel(modifier = Modifier.fillMaxWidth()) }\n                }\n\n                MainSection.Settings -> {\n"""
        s = replace_once(s, settings_anchor, downloads_branch, "Android Downloads section")
        return s
    patch(mobile, patch_mobile)
    print("Stremio Sense Android MVP installed")

if __name__ == "__main__":
    main()
