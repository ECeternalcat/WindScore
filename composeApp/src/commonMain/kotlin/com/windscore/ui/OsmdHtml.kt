package com.windscore.ui

/** OSMD（OpenSheetMusic Display）看谱模式的共享 HTML 构建（文档 4.4）。 */
internal object OsmdHtml {

    // 固定稳定版 1.8.9；正确产物路径为 /build/opensheetmusicdisplay.min.js
    private const val OSMD_SRC = "https://cdn.jsdelivr.net/npm/opensheetmusicdisplay@1.8.9/build/opensheetmusicdisplay.min.js"

    /**
     * 构建加载 OSMD 并渲染 [rawXml] 的 HTML。
     * 原始 MusicXML 放入 type="text/xml" 的 script 标签，避免 JS 字符串转义。
     * 暴露 window.__advanceCursor(n) 供原生侧驱动光标步进/自动翻页。
     */
    fun build(rawXml: String): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
            <style>
                html,body{margin:0;padding:0;background:#ffffff;font-family:sans-serif;}
                #osmd{padding:6px;}
                #err{color:#b00;padding:12px;display:none;}
            </style>
            <script src="$OSMD_SRC"></script>
        </head>
        <body>
            <div id="osmd"></div>
            <div id="err"></div>
            <script type="text/xml" id="score-data">
$rawXml
            </script>
            <script>
                window.__advanceCursor = function (n) {
                    if (!window.__osmd || !window.__osmd.cursor) return;
                    for (var i = 0; i < n; i++) window.__osmd.cursor.next();
                };
                function fail(msg){
                    var e = document.getElementById('err');
                    e.style.display='block';
                    e.textContent = '乐谱渲染失败: ' + msg;
                }
                window.onload = function () {
                    try {
                        if (typeof opensheetmusicdisplay === 'undefined') { fail('OSMD 未加载（需联网）'); return; }
                        var osmd = new opensheetmusicdisplay.OpenSheetMusicDisplay('osmd', {
                            autoResize: true,
                            backend: 'svg',
                            drawCursors: true
                        });
                        var xml = document.getElementById('score-data').textContent.trim();
                        osmd.load(xml).then(function () {
                            osmd.render();
                            osmd.cursor.show();
                            window.__osmd = osmd;
                        }).catch(function (ex) { fail(ex && ex.message || ex); });
                    } catch (ex) { fail(ex && ex.message || ex); }
                };
            </script>
        </body>
        </html>
    """.trimIndent()
}
