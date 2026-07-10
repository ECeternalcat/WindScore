package com.windscore.ui

/**
 * OSMD 2.0 看谱模式 — 方案C：原生滚动，固定zoom。
 *
 * - HTTP Server 提供 XML（不用 JS Bridge）
 * - endless 模式 + load 后设 pageWidth
 * - 固定 zoom 锁定横向铺满
 * - overflow-y: auto 原生滚动，CSS 隐藏滚动条
 * - cursor scrollIntoView 自动追焦
 * - moveToTick 驱动光标
 */
internal object OsmdHtml {

    fun buildHtml(divisions: Int): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8"/>
            <style>
                html,body{margin:0;padding:0;height:100%;background:#fff;overflow:hidden;}
                #osmd{width:100%;height:100%;overflow-y:auto;overflow-x:hidden;}
                #osmd::-webkit-scrollbar{width:0;}
                #osmd{scrollbar-width:none;-ms-overflow-style:none;}
                #err{position:fixed;top:0;left:0;right:0;color:#b00;padding:12px;display:none;background:#fff;z-index:99;font-size:13px;}
                #hud{position:fixed;top:4px;right:8px;font-size:11px;color:#555;background:rgba(255,255,255,0.8);z-index:98;pointer-events:none;}
                #loading{position:fixed;top:0;left:0;width:100%;height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;background:rgba(255,255,255,0.92);z-index:97;}
                #loading-text{margin-top:16px;color:#555;font-size:15px;}
                .spinner{width:44px;height:44px;border:4px solid #e6e6e6;border-top-color:#000;border-radius:50%;animation:spin .8s linear infinite;}
                @keyframes spin{to{transform:rotate(360deg);}}
            </style>
            <script src="osmd.js"></script>
        </head>
        <body>
            <div id="hud"></div>
            <div id="loading"><div class="spinner"></div><div id="loading-text">正在加载乐谱…</div></div>
            <div id="osmd"></div>
            <div id="err"></div>
            <script>
                window.__osmd=null;
                window.__renderTime=-1;
                window.__status="init";
                window.__cursorTimes=[];
                window.__cursorIdx=0;
                window.__divisions=$divisions;

                function setStatus(s){window.__status=s;try{document.getElementById('hud').textContent=s;}catch(e){}}
                function fail(msg){var e=document.getElementById('err');e.style.display='block';e.textContent=''+msg+' ['+window.__status+']';document.getElementById('loading').style.display='none';}
                function fracReal(f){if(f==null)return null;if(typeof f==="number")return f;if(f.realValue!=null)return f.realValue;if(f.RealValue!=null)return f.RealValue;if(typeof f.value==="function")return f.value();if(typeof f.Value==="function")return f.Value();if(f.numerator!=null&&f.denominator!=null)return f.numerator/f.denominator;return null;}

                function buildCursorTimes(){
                    var osmd=window.__osmd;
                    window.__cursorTimes=[];
                    window.__cursorIdx=0;
                    if(!osmd||!osmd.cursor)return;
                    try{
                        osmd.cursor.reset();osmd.cursor.show();
                        var it=osmd.cursor.iterator;
                        var safety=300000;
                        while(safety-->0){
                            var t=fracReal(it.currentTimeStamp);
                            window.__cursorTimes.push(t!=null?t:0);
                            if(it.EndReached)break;
                            osmd.cursor.next();
                        }
                    }catch(e){}
                    try{osmd.cursor.reset();osmd.cursor.show();}catch(e){}
                }

                window.moveToTick=function(targetTick){
                    var osmd=window.__osmd;
                    if(!osmd||!osmd.cursor)return;
                    var times=window.__cursorTimes;
                    if(!times||times.length===0)return;
                    var divisions=window.__divisions||4;
                    var posWhole=targetTick/(4.0*divisions);
                    var target=window.__cursorIdx;
                    for(var j=window.__cursorIdx+1;j<times.length;j++){
                        if((times[j]||0)>posWhole)break;
                        target=j;
                    }
                    if(target===window.__cursorIdx)return;
                    for(var k=window.__cursorIdx+1;k<=target;k++){
                        try{osmd.cursor.next();}catch(e){}
                    }
                    window.__cursorIdx=target;
                    try{osmd.cursor.show();}catch(e){}
                    try{
                        var el=osmd.cursor.cursorElement;
                        if(el&&el.scrollIntoView)el.scrollIntoView({block:"center"});
                    }catch(e){}
                };

                window.__resetCursor=function(){
                    var osmd=window.__osmd;
                    if(!osmd||!osmd.cursor)return;
                    try{osmd.cursor.reset();osmd.cursor.show();}catch(e){}
                    window.__cursorIdx=0;
                };

                window.onload=function(){
                    try{
                        setStatus("获取XML");
                        if(typeof opensheetmusicdisplay==='undefined'){fail('OSMD未加载');return;}
                        var cW=document.getElementById('osmd').clientWidth||1200;
                        var cH=document.getElementById('osmd').clientHeight||700;
                        fetch('score.xml',{cache:'no-store'})
                            .then(function(r){if(!r.ok)throw new Error('HTTP '+r.status);return r.text();})
                            .then(function(rawXml){
                                var xml=rawXml.replace(/<!DOCTYPE[^>]*>/i,'');
                                setStatus("创建OSMD");
                                var osmd=new opensheetmusicdisplay.OpenSheetMusicDisplay('osmd',{
                                    drawCursors:true,
                                    drawTitle:false,
                                    drawPartNames:false
                                });
                                window.__osmd=osmd;
                                setStatus("解析 "+(xml.length/1024).toFixed(0)+"KB");
                                var t0=Date.now();
                                osmd.load(xml).then(function(){
                                    // endless 模式设 pageWidth + 固定 zoom
                                    var pw=Math.round(cW/3.78);
                                    if(osmd.Sheet)osmd.Sheet.pageWidth=pw;
                                    var zoom=Math.max(0.5,Math.min(cW/800.0,2.0));
                                    osmd.Zoom=zoom;
                                    osmd.updateGraphic();
                                    osmd.render();
                                    window.__renderTime=Date.now()-t0;
                                    document.getElementById('loading').style.display='none';
                                    try{osmd.cursor.show();}catch(e){}
                                    buildCursorTimes();
                                    setStatus("就绪 "+window.__renderTime.toFixed(0)+"ms");
                                }).catch(function(ex){fail(ex&&(ex.message||ex)||ex);});
                            })
                            .catch(function(ex){fail('XML获取失败: '+(ex&&ex.message||ex));});
                    }catch(ex){fail(ex&&(ex.message||ex)||ex);}
                };
                setTimeout(function(){if(window.__renderTime<0)fail('超时>'+window.__status);},120000);
            </script>
        </body>
        </html>
    """.trimIndent()
}
