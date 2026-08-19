var BorderFX = require('ti.borderfx');

var win = Ti.UI.createWindow({ backgroundColor: '#000' });

var scroll = Ti.UI.createScrollView({
    top: 60, bottom: 0, left: 0, right: 0,
    layout: 'vertical',
    contentWidth: Ti.UI.FILL,
    contentHeight: Ti.UI.SIZE,
    showVerticalScrollIndicator: true
});
win.add(scroll);

scroll.add(Ti.UI.createLabel({
    text: 'BorderFX — Playground de testes',
    color: '#fff',
    font: { fontSize: 20, fontWeight: 'bold' },
    top: 24, left: 20, right: 20,
    height: Ti.UI.SIZE
}));

// ---------- helpers ----------

function section(title, subtitle) {
    var wrap = Ti.UI.createView({ layout: 'vertical', top: 32, width: Ti.UI.FILL, height: Ti.UI.SIZE });

    wrap.add(Ti.UI.createLabel({
        text: title,
        color: '#ffffff',
        font: { fontSize: 22, fontWeight: '600' },
        left: 20, right: 20,
        height: Ti.UI.SIZE
    }));

    if (subtitle) {
        wrap.add(Ti.UI.createLabel({
            text: subtitle,
            color: '#ffffff',
			opacity: 0.5,
            font: { fontSize: 17 },
            left: 20, right: 20, top: 6,
            height: Ti.UI.SIZE
        }));
    }

    scroll.add(wrap);
    return wrap;
}

function card(width, height, radius) {
    return Ti.UI.createView({
        width: width || 300,
        height: height || 120,
        borderRadius: (radius === undefined) ? 18 : radius,
        backgroundColor: '#343434',
        top: 16,
        left: 20
    });
}

function row() {
    return Ti.UI.createView({ layout: 'horizontal', width: Ti.UI.FILL, height: Ti.UI.SIZE, top: 10, left: 20 });
}

var PALETTES = [
    ['#FF2D55', '#5AC8FA', '#FFD60A'],
    ['#34C759', '#00C7BE', '#5856D6'],
    ['#FF9500', '#FF375F', '#AF52DE'],
    ['#32ADE6', '#63E6E2', '#FFD60A']
];
function randomPalette() {
    return PALETTES[Math.floor(Math.random() * PALETTES.length)];
}

// ---------------------------------------------------------------
// 1. mode: 'beam' — segmento curto correndo pela borda
// ---------------------------------------------------------------
var s1 = section('1. mode: "beam"', 'Segmento curto percorrendo o contorno, sem giro do fundo');
var c1 = card();
s1.add(c1);
BorderFX.attach(c1, {
    mode: 'beam',
    colors: ['#FF2D55', '#FF9500', '#FFD60A'],
    borderWidth: 3,
    beamLength: 0.2,
    duration: 2000
}).start();

// ---------------------------------------------------------------
// 2. mode: 'rotate' — anel inteiro com gradiente cônico girando
// ---------------------------------------------------------------
var s2 = section('2. mode: "rotate"', 'Anel completo — as cores "andam" pela borda inteira');
var c2 = card();
s2.add(c2);
BorderFX.attach(c2, {
    mode: 'rotate',
    colors: ['#5AC8FA', '#5856D6', '#AF52DE', '#5AC8FA'],
    borderWidth: 3,
    duration: 3000
}).start();

// ---------------------------------------------------------------
// 3. mode: 'both' — combinação dos dois
// ---------------------------------------------------------------
var s3 = section('3. mode: "both"', 'Segmento + gradiente girando ao mesmo tempo (o efeito original que você pediu)');
var c3 = card();
s3.add(c3);
BorderFX.attach(c3, {
    mode: 'both',
    colors: ['#34C759', '#30D158', '#00C7BE', '#34C759'],
    borderWidth: 3,
    beamLength: 0.22,
    duration: 2400
}).start();

// ---------------------------------------------------------------
// 4. borderWidth — fina vs grossa
// ---------------------------------------------------------------
var s4 = section('4. borderWidth', '1pt vs 8pt');
var row4 = row();
s4.add(row4);
var c4a = card(135, 100); c4a.left = 0;
var c4b = card(135, 100); c4b.left = 10;
row4.add(c4a); row4.add(c4b);
BorderFX.attach(c4a, { mode: 'beam', colors: ['#FF375F', '#FF9F0A'], borderWidth: 1, beamLength: 0.3, duration: 1800 }).start();
BorderFX.attach(c4b, { mode: 'beam', colors: ['#FF375F', '#FF9F0A'], borderWidth: 8, beamLength: 0.3, duration: 1800 }).start();

// ---------------------------------------------------------------
// 5. beamLength — curto vs longo
// ---------------------------------------------------------------
var s5 = section('5. beamLength', '6% do perímetro vs 70% do perímetro');
var row5 = row();
s5.add(row5);
var c5a = card(135, 100); c5a.left = 0;
var c5b = card(135, 100); c5b.left = 10;
row5.add(c5a); row5.add(c5b);
BorderFX.attach(c5a, { mode: 'beam', colors: ['#64D2FF', '#0A84FF'], borderWidth: 3, beamLength: 0.06, duration: 2000 }).start();
BorderFX.attach(c5b, { mode: 'beam', colors: ['#64D2FF', '#0A84FF'], borderWidth: 3, beamLength: 0.7, duration: 2000 }).start();

// ---------------------------------------------------------------
// 6. duration — rápido vs lento
// ---------------------------------------------------------------
var s6 = section('6. duration', '700ms por volta vs 6000ms por volta');
var row6 = row();
s6.add(row6);
var c6a = card(135, 100); c6a.left = 0;
var c6b = card(135, 100); c6b.left = 10;
row6.add(c6a); row6.add(c6b);
BorderFX.attach(c6a, { mode: 'both', colors: ['#FF453A', '#FF9F0A'], borderWidth: 3, beamLength: 0.25, duration: 700 }).start();
BorderFX.attach(c6b, { mode: 'both', colors: ['#FF453A', '#FF9F0A'], borderWidth: 3, beamLength: 0.25, duration: 6000 }).start();

// ---------------------------------------------------------------
// 7. paleta de cores — 2 cores vs arco-íris completo
// ---------------------------------------------------------------
var s7 = section('7. colors', '2 cores vs paleta arco-íris (repita a 1ª cor no final pra não ter costura)');
var row7 = row();
s7.add(row7);
var c7a = card(135, 100); c7a.left = 0;
var c7b = card(135, 100); c7b.left = 10;
row7.add(c7a); row7.add(c7b);
BorderFX.attach(c7a, { mode: 'rotate', colors: ['#FF2D55', '#5AC8FA'], borderWidth: 3, duration: 2200 }).start();
BorderFX.attach(c7b, {
    mode: 'rotate',
    colors: ['#FF3B30', '#FF9500', '#FFCC00', '#34C759', '#5AC8FA', '#5856D6', '#FF3B30'],
    borderWidth: 3,
    duration: 2200
}).start();

// ---------------------------------------------------------------
// 8. cantos — reto vs pill (radius = height/2)
// ---------------------------------------------------------------
var s8 = section('8. borderRadius da view alvo', 'Canto reto (0) vs pill (radius = altura/2) — o path acompanha sempre');
var row8 = row();
s8.add(row8);
var c8a = card(140, 70, 0); c8a.left = 0;
var c8b = card(140, 70, 35); c8b.left = 10;
row8.add(c8a); row8.add(c8b);
BorderFX.attach(c8a, { mode: 'rotate', colors: ['#FF375F', '#FFD60A'], borderWidth: 2, duration: 2000 }).start();
BorderFX.attach(c8b, { mode: 'rotate', colors: ['#5AC8FA', '#34C759'], borderWidth: 2, duration: 2000 }).start();

// ---------------------------------------------------------------
// 9. controles interativos — start / stop / updateColors / destroy
// ---------------------------------------------------------------
var s9 = section('9. Controles ao vivo', 'start(), stop(), updateColors(), destroy()');
var c9 = card();
s9.add(c9);
var effect9 = BorderFX.attach(c9, {
    mode: 'both',
    colors: ['#FF2D55', '#5AC8FA', '#FFD60A'],
    borderWidth: 3,
    beamLength: 0.22,
    duration: 2200
});
effect9.start();

var row9 = row();
s9.add(row9);
var running9 = true;
var btnToggle = Ti.UI.createButton({ title: 'Stop', width: 90, left: 0 });
var btnColors = Ti.UI.createButton({ title: 'Trocar cores', width: 130, left: 10 });
var btnDestroy = Ti.UI.createButton({ title: 'Destruir', width: 100, left: 10 });
btnToggle.addEventListener('click', function () {
    if (running9) { effect9.stop(); btnToggle.title = 'Start'; }
    else { effect9.start(); btnToggle.title = 'Stop'; }
    running9 = !running9;
});
btnColors.addEventListener('click', function () {
    effect9.updateColors(randomPalette());
});
btnDestroy.addEventListener('click', function () {
    effect9.destroy();
    btnToggle.enabled = false;
    btnColors.enabled = false;
    btnDestroy.enabled = false;
});
row9.add(btnToggle); row9.add(btnColors); row9.add(btnDestroy);

// ---------------------------------------------------------------
// 10. resize dinâmico (autolayout) — sem recriar o efeito
// ---------------------------------------------------------------
var s10 = section('10. Resize dinâmico', 'O CADisplayLink acompanha sozinho, sem recriar o efeito');
var c10 = card(220, 100);
s10.add(c10);
BorderFX.attach(c10, {
    mode: 'both', colors: ['#FFD60A', '#FF9500', '#FF2D55'], borderWidth: 3, beamLength: 0.25, duration: 2000
}).start();
var big10 = false;
var btnResize = Ti.UI.createButton({ title: 'Redimensionar', top: 10, left: 20 });
btnResize.addEventListener('click', function () {
    big10 = !big10;
    c10.animate({ width: big10 ? 320 : 220, height: big10 ? 160 : 100, duration: 350 });
});
s10.add(btnResize);

// ---------------------------------------------------------------
// 11. múltiplas instâncias simultâneas (teste de performance)
// ---------------------------------------------------------------
var s11 = section('11. Várias instâncias ao mesmo tempo', 'Seis cards, três modos diferentes, animando juntos');
var scrollRow = Ti.UI.createScrollView({
    layout: 'horizontal',
    height: 110,
    width: Ti.UI.FILL,
    left: 20,
    top: 10,
    showHorizontalScrollIndicator: true
});
s11.add(scrollRow);
var MODES = ['beam', 'rotate', 'both'];
for (var i = 0; i < 6; i++) {
    var mini = card(100, 90, 14);
    mini.left = (i === 0) ? 0 : 10;
    scrollRow.add(mini);
    BorderFX.attach(mini, {
        mode: MODES[i % MODES.length],
        colors: randomPalette(),
        borderWidth: 2,
        beamLength: 0.25,
        duration: 1500 + i * 300
    }).start();
}

scroll.add(Ti.UI.createView({ height: 50 }));

win.open();