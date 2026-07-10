package com.windscore.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 莨扈溽恚隹ｱ讓｡蠑擾ｼ域枚譯｣ 4.4・会ｼ夂畑 WebView 蜉霓ｽ OSMD 貂ｲ譟謎ｺ皮ｺｿ隹ｱ蟷ｶ閾ｪ蜉ｨ鄙ｻ鬘ｵ縲・ *
 * - Android・哂ndroidView + WebView・侫esktop・哽avaFX WebView・・wingPanel 蠏悟・・峨・ * - [currentTickProvider] 謠蝉ｾ帶眺謾ｾ/扈・ｹ貂ｸ譬・tick・帶困邂玲・ whole-note 菴咲ｽｮ蜷朱ｩｱ蜉ｨ OSMD 蜈画・ *   ・亥渕莠・OSMD 閾ｪ霄ｫ譌ｶ髣ｴ謌ｳ閾ｪ譬｡蜃・ｼ檎ｲｾ遑ｮ霍滄囂蠖灘燕髻ｳ隨ｦ・会ｼ悟ｹｶ闃よｵ∬・蜉ｨ貊壼勘蛻ｰ隗・哨荳ｭ螟ｮ縲・ * - [onRenderTime] 蝗櫁ｰ・load+render 閠玲慮・域ｸｲ譟捺ｧ閭ｽ豬玖ｯ包ｼ峨・ *
 * 荳･遖∫畑 Compose 謇句・莠皮ｺｿ隹ｱ謗堤沿窶披皮峩謗･螟咲畑 OSMD 逧・∩隶ｩ荳守ｿｻ鬘ｵ邂玲ｳ輔・ */
@Composable
expect fun ScoreSheetView(
    rawXml: String,
    currentTickProvider: () -> Long,
    modifier: Modifier = Modifier,
    divisions: Int = 4,
    pages: Int = 1,
    onRenderTime: (Double) -> Unit = {},
)
