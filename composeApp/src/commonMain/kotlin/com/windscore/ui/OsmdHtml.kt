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
                *:focus{outline:none;}
                #osmd{width:100%;height:100%;overflow:hidden;touch-action:none;user-select:none;-webkit-user-select:none;-webkit-tap-highlight-color:transparent;}
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
                window.__pendingSeek=0;
                window.__pendingStep=0;

                function setStatus(s){window.__status=s;try{document.getElementById('hud').textContent=s;}catch(e){}}
                function fail(msg){var e=document.getElementById('err');e.style.display='block';e.textContent=''+msg+' ['+window.__status+']';document.getElementById('loading').style.display='none';}
                function fracReal(f){if(f==null)return null;if(typeof f==="number")return f;if(f.realValue!=null)return f.realValue;if(f.RealValue!=null)return f.RealValue;if(typeof f.value==="function")return f.value();if(typeof f.Value==="function")return f.Value();if(f.numerator!=null&&f.denominator!=null)return f.numerator/f.denominator;return null;}

                function buildCursorTimes(){
                    var osmd=window.__osmd;
                    window.__cursorTimes=[];
                    window.__cursorPos=[];
                    window.__cursorIdx=0;
                    if(!osmd||!osmd.cursor)return;
                    try{
                        osmd.cursor.reset();osmd.cursor.show();
                        var it=osmd.cursor.iterator;
                        var safety=300000;
                        var ctEl=document.getElementById('osmd');
                        while(safety-->0){
                            var t=fracReal(it.currentTimeStamp);
                            window.__cursorTimes.push(t!=null?t:0);
                            var el=osmd.cursor.cursorElement;
                            if(el&&ctEl){
                                var r=el.getBoundingClientRect();
                                var cr=ctEl.getBoundingClientRect();
                                window.__cursorPos.push(r.left-cr.left,r.top-cr.top,r.height);
                            }else{
                                window.__cursorPos.push(0,0,0);
                            }
                            if(it.EndReached)break;
                            osmd.cursor.next();
                        }
                    }catch(e){}
                    try{osmd.cursor.reset();osmd.cursor.show();}catch(e){}
                }

                // 分块截图：每块克隆 SVG 并设 viewBox 只渲染该区域，避免超大 Image 加载失败
                window.captureSnapshot=function(){
                    var osmdEl=document.getElementById('osmd');
                    var svg=document.querySelector('#osmd svg');
                    if(!svg||!osmdEl){window.__snapshotChunksReady=1;window.__snapshotChunkCount=1;window.__snapshotError='no svg';return;}
                    var rect=svg.getBoundingClientRect();
                    var w=Math.ceil(rect.width)||osmdEl.scrollWidth;
                    var h=Math.ceil(rect.height)||osmdEl.scrollHeight;
                    var CHUNK=4000;
                    var numChunks=Math.ceil(h/CHUNK);
                    window.__snapshotChunks=new Array(numChunks);
                    window.__snapshotChunkWidth=w;
                    window.__snapshotTotalHeight=h;
                    window.__snapshotChunkCount=numChunks;
                    window.__snapshotChunksReady=0;
                    for(var i=0;i<numChunks;i++){
                        var cy=i*CHUNK;
                        var ch=Math.min(CHUNK,h-cy);
                        var svgClone=svg.cloneNode(true);
                        svgClone.setAttribute('width',w);
                        svgClone.setAttribute('height',ch);
                        svgClone.setAttribute('viewBox','0 '+cy+' '+w+' '+ch);
                        var svgData=new XMLSerializer().serializeToString(svgClone);
                        var canvas=document.createElement('canvas');
                        canvas.width=w;canvas.height=ch;
                        var ctx=canvas.getContext('2d');
                        ctx.fillStyle='#fff';
                        ctx.fillRect(0,0,w,ch);
                        (function(idx,context,canvasEl,dataUrl){
                            var img=new Image();
                            img.onload=function(){
                                context.drawImage(img,0,0);
                                try{window.__snapshotChunks[idx]=canvasEl.toDataURL('image/png');}catch(e){window.__snapshotError='chunk'+idx+':'+e.message;}
                                window.__snapshotChunksReady++;
                            };
                            img.onerror=function(){window.__snapshotError='img load error chunk'+idx;window.__snapshotChunksReady++;};
                            var blob=new Blob([dataUrl],{type:'image/svg+xml;charset=utf-8'});
                            img.src=URL.createObjectURL(blob);
                        })(i,ctx,canvas,svgData);
                    }
                };

                // 合并“命令下发 + 光标推进”，一次 executeScript 完成。
                // 返回字符串命令供 Kotlin 解析：
                //   ""            无待处理命令
                //   "seek:<tick>" 宏观小节吸附
                //   "step:1"      微观前进一个音符
                //   "step:-1"     微观后退一个音符
                window.tickAndSeek=function(targetTick){
                    var cmd="";
                    if(window.__pendingSeek>0){
                        cmd="seek:"+window.__pendingSeek;
                        window.__pendingSeek=0;
                    }else if(window.__pendingStep!==0){
                        cmd="step:"+window.__pendingStep;
                        window.__pendingStep=0;
                    }
                    var osmd=window.__osmd;
                    if(!osmd||!osmd.cursor)return cmd;
                    var times=window.__cursorTimes;
                    if(!times||times.length===0)return cmd;
                    var divisions=window.__divisions||4;
                    var posWhole=targetTick/(4.0*divisions);
                    var target=window.__cursorIdx;
                    for(var j=window.__cursorIdx+1;j<times.length;j++){
                        if((times[j]||0)>posWhole)break;
                        target=j;
                    }
                    if(target!==window.__cursorIdx){
                        for(var k=window.__cursorIdx+1;k<=target;k++){
                            try{osmd.cursor.next();}catch(e){}
                        }
                        window.__cursorIdx=target;
                        try{osmd.cursor.show();}catch(e){}
                        try{
                            var el=osmd.cursor.cursorElement;
                            if(el){
                                var ct=document.getElementById('osmd');
                                var cr=ct.getBoundingClientRect();
                                var er=el.getBoundingClientRect();
                                var cursorTop=er.top-cr.top;
                                var cursorBot=er.bottom-cr.top;
                                if(cursorTop<50||cursorBot>cr.height-50){
                                    el.scrollIntoView({block:"center"});
                                }
                            }
                        }catch(e){}
                    }
                    return cmd;
                };

                window.__resetCursor=function(){
                    var osmd=window.__osmd;
                    if(!osmd||!osmd.cursor)return;
                    try{
                        osmd.cursor.reset();osmd.cursor.show();
                        var el=osmd.cursor.cursorElement;
                        if(el&&el.scrollIntoView)el.scrollIntoView({block:"center"});
                    }catch(e){}
                    window.__cursorIdx=0;
                };

                // —— 宏观定位 + 微观盲操（文档 3.3）——
                var isScrubbing=false;
                var scrubAnchorX=0;
                var SCRUB_THRESHOLD=40;
                var lastTouchTime=0;

                // 宏观小节吸附：遍历 musicPages→MusicSystems，按 Y 最近找系统行，
                // 再按 X 最近找小节，命中则取 parentSourceMeasure.AbsoluteTimestamp 转 tick。
                function macroSnapAt(clientX,clientY){
                    var osmd=window.__osmd;
                    if(!osmd||!osmd.GraphicSheet)return false;
                    var pages=osmd.GraphicSheet.musicPages;
                    if(!pages||pages.length===0)return false;
                    // 汇集所有页面的 MusicSystem（兼容大小写）
                    var systems=[];
                    for(var pi=0;pi<pages.length;pi++){
                        var ms=pages[pi].MusicSystems||pages[pi].musicSystems;
                        if(ms){for(var si=0;si<ms.length;si++)systems.push(ms[si]);}
                    }
                    if(systems.length===0)return false;
                    var ct=document.getElementById('osmd');
                    var rect=ct.getBoundingClientRect();
                    var zoom=osmd.Zoom||osmd.zoom||1.0;
                    var unitX=(clientX-rect.left)/(10.0*zoom);
                    var unitY=(clientY-rect.top+ct.scrollTop)/(10.0*zoom);
                    // 最近系统行匹配（Y 到边界框距离最小，消除相邻行容差重叠）
                    var sys=null;
                    var bestDist=1e9;
                    for(var i=0;i<systems.length;i++){
                        var sb=systems[i].PositionAndShape;
                        if(!sb)continue;
                        var sy=sb.AbsolutePosition?sb.AbsolutePosition.y:0;
                        var sh=sb.Size?sb.Size.height:0;
                        var d=unitY<sy?(sy-unitY):(unitY>sy+sh?(unitY-sy-sh):0);
                        if(d<bestDist){bestDist=d;sys=systems[i];}
                    }
                    if(!sys||!sys.StaffLines||sys.StaffLines.length===0)return false;
                    var measures=sys.StaffLines[0].Measures;
                    if(!measures)return false;
                    // 最近小节匹配（X 到边界框距离最小）
                    var hitIdx=-1;
                    bestDist=1e9;
                    for(var j=0;j<measures.length;j++){
                        var mb=measures[j].PositionAndShape;
                        if(!mb)continue;
                        var mx=mb.AbsolutePosition?mb.AbsolutePosition.x:0;
                        var mw=mb.Size?mb.Size.width:0;
                        var dx=unitX<mx?(mx-unitX):(unitX>mx+mw?(unitX-mx-mw):0);
                        if(dx<bestDist){bestDist=dx;hitIdx=j;}
                    }
                    if(hitIdx<0)return false;
                    var psm=measures[hitIdx].parentSourceMeasure;
                    if(psm&&psm.AbsoluteTimestamp){
                        var real=fracReal(psm.AbsoluteTimestamp);
                        if(real!=null){
                            var divisions=window.__divisions||4;
                            window.__pendingSeek=Math.round(real*4*divisions);
                            return true;
                        }
                    }
                    return false;
                }

                function startScrub(clientX,clientY){
                    if(macroSnapAt(clientX,clientY)){
                        isScrubbing=true;
                        scrubAnchorX=clientX;
                    }
                }
                function moveScrub(clientX){
                    if(!isScrubbing)return;
                    var dx=clientX-scrubAnchorX;
                    if(dx>SCRUB_THRESHOLD){window.__pendingStep=1;scrubAnchorX=clientX;}
                    else if(dx<-SCRUB_THRESHOLD){window.__pendingStep=-1;scrubAnchorX=clientX;}
                }
                function endScrub(){isScrubbing=false;}

                function initInteraction(){
                    var el=document.getElementById('osmd');
                    if(!el||el.__interactionBound)return;
                    el.__interactionBound=true;
                    // 鼠标事件（桌面 JavaFX WebView 仅发 MouseEvents）
                    el.addEventListener('mousedown',function(e){
                        if(Date.now()-lastTouchTime<800)return;
                        startScrub(e.clientX,e.clientY);
                        if(isScrubbing)try{e.preventDefault();}catch(_){}
                    });
                    window.addEventListener('mousemove',function(e){moveScrub(e.clientX);});
                    window.addEventListener('mouseup',endScrub);
                    // 触摸事件（移动端）；scrubbing 时 touchmove preventDefault 锁定竖向滚动
                    el.addEventListener('touchstart',function(e){
                        var t=e.touches[0];
                        if(!t)return;
                        lastTouchTime=Date.now();
                        startScrub(t.clientX,t.clientY);
                        if(isScrubbing)try{e.preventDefault();}catch(_){}
                    },{passive:false});
                    window.addEventListener('touchmove',function(e){
                        if(!isScrubbing)return;
                        var t=e.touches[0];
                        if(t)moveScrub(t.clientX);
                        try{e.preventDefault();}catch(_){}
                    },{passive:false});
                    window.addEventListener('touchend',endScrub);
                    window.addEventListener('touchcancel',endScrub);

                    // JS 手动滚动（overflow:hidden 下 scrollTop 仍可编程设置）
                    el.addEventListener('wheel',function(e){
                        el.scrollTop+=e.deltaY;
                        try{e.preventDefault();}catch(_){}
                    },{passive:false});
                    // 触摸竖向滚动（非 scrubbing 时）
                    var touchScrollY=0;
                    el.addEventListener('touchstart',function(e){
                        if(!isScrubbing){touchScrollY=e.touches[0].clientY;}
                    },{passive:false});
                    el.addEventListener('touchmove',function(e){
                        if(isScrubbing)return;
                        var t=e.touches[0];
                        if(!t)return;
                        var dy=touchScrollY-t.clientY;
                        if(Math.abs(dy)>2){
                            el.scrollTop+=dy;
                            touchScrollY=t.clientY;
                        }
                        try{e.preventDefault();}catch(_){}
                    },{passive:false});
                }

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
                                    autoResize:false,
                                    drawCursors:true,
                                    drawTitle:false,
                                    drawPartNames:false
                                });
                                window.__osmd=osmd;
                                setStatus("解析 "+(xml.length/1024).toFixed(0)+"KB");
                                var t0=Date.now();
                                osmd.load(xml).then(function(){
                                    // 两步渲染：OSMD 内部单位与像素的换算因子取决于
                                    // MusicXML 的 <scaling> 设置，无法用固定公式推算。
                                    // 因此先以 zoom=1 渲染一次测量 SVG 实际像素宽度，
                                    // 再算出正确的 zoom 重新渲染使其铺满容器。
                                    setStatus("测量宽度");
                                    osmd.Zoom=1.0;
                                    osmd.updateGraphic();
                                    osmd.render();
                                    var svg=document.querySelector('#osmd svg');
                                    var svgW=0;
                                    if(svg){
                                        svgW=svg.getBoundingClientRect().width;
                                        if(!svgW||svgW<10) svgW=parseFloat(svg.getAttribute('width'))||0;
                                    }
                                    if(svgW>10){
                                        var fitZoom=cW/svgW;
                                        fitZoom=Math.max(0.3,Math.min(fitZoom,4.0));
                                        setStatus("适配 zoom="+fitZoom.toFixed(2));
                                        osmd.Zoom=fitZoom;
                                        osmd.updateGraphic();
                                        osmd.render();

                                        // 两步渲染可能导致微小的系统换行偏移，在此使用 transform 强力缩放对齐
                                        var svg2=document.querySelector('#osmd svg');
                                        if(svg2){
                                            var svgW2=svg2.getBoundingClientRect().width;
                                            if(!svgW2||svgW2<10) svgW2=parseFloat(svg2.getAttribute('width'))||0;
                                            if(svgW2>10 && Math.abs(svgW2 - cW) / cW > 0.03){
                                                var scale=cW/svgW2;
                                                var osmdEl=document.getElementById('osmd');
                                                osmdEl.style.transform='scale('+scale+')';
                                                osmdEl.style.transformOrigin='top left';
                                            }
                                        }
                                    }
                                    window.__renderTime=Date.now()-t0;
                                    document.getElementById('loading').style.display='none';
                                    try{osmd.cursor.show();}catch(e){}
                                    buildCursorTimes();
                                    setStatus("就绪 "+window.__renderTime.toFixed(0)+"ms");
                                    initInteraction();
                                    // 渲染完成后截图（SVG→PNG）
                                    setTimeout(function(){window.captureSnapshot&&window.captureSnapshot();},100);
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
