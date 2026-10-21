uniform sampler2D blurmap0;
varying highp vec2 texCoord;
uniform highp vec2 randomOffset;
uniform highp vec2 lightDir;
void main ()
{
  highp float r;
  highp vec3 y;
  highp vec3 g;
  highp float x;
  highp float b;
  b = (157.5 + ((lightDir.x * 90.0) - 45.0));
  r = b;
  if ((randomOffset.x < 0.25)) {
    x = (0.2 * randomOffset.x);
    highp float z;
    z = (0.7 + x);
    highp float a;
    highp float _;
    highp float A;
    highp float Y;
    highp float R;
    highp float X;
    X = ((6.0 + z) / 2048.0);
    highp float W;
    W = ((6.0 + z) / 2048.0);
    lowp float Z;
    Z = texture2D (blurmap0, texCoord).w;
    Y = Z;
    Y = (1.0 - ((Y * Y) * (Y * Y)));
    highp float o;
    o = (((b - 180.0) * 3.14159) / 180.0);
    highp float O;
    O = cos(o);
    mediump float I;
    I = (X * O);
    highp float i;
    i = sin(o);
    mediump float l;
    l = (X * i);
    mediump vec2 S;
    S.x = I;
    S.y = l;
    highp vec2 G;
    G = (texCoord + S);
    highp float B;
    B = (-45.0 + b);
    B = (((B - 180.0) * 3.14159) / 180.0);
    highp float c;
    c = cos(B);
    mediump float d;
    d = (X * c);
    highp float e;
    e = sin(B);
    mediump float f;
    f = (X * e);
    mediump vec2 h;
    h.x = d;
    h.y = f;
    highp vec2 j;
    j = (texCoord + h);
    highp float k;
    k = (-90.0 + b);
    k = (((k - 180.0) * 3.14159) / 180.0);
    highp float m;
    m = cos(k);
    mediump float n;
    n = (X * m);
    highp float p;
    p = sin(k);
    mediump float q;
    q = (X * p);
    mediump vec2 s;
    s.x = n;
    s.y = q;
    highp vec2 t;
    t = (texCoord + s);
    highp float u;
    u = (-135.0 + b);
    u = (((u - 180.0) * 3.14159) / 180.0);
    highp float v;
    v = cos(u);
    mediump float C;
    C = (X * v);
    highp float D;
    D = sin(u);
    mediump float E;
    E = (X * D);
    mediump vec2 F;
    F.x = C;
    F.y = E;
    highp vec2 H;
    H = (texCoord + F);
    lowp float J;
    J = (1.0 - ((texture2D (blurmap0, G).w * texture2D (blurmap0, j).w) * (texture2D (blurmap0, t).w * texture2D (blurmap0, H).w)));
    A = J;
    highp float K;
    K = (45.0 + b);
    K = (((K - 180.0) * 3.14159) / 180.0);
    highp float L;
    L = cos(K);
    mediump float M;
    M = (W * L);
    highp float N;
    N = sin(K);
    mediump float P;
    P = (W * N);
    mediump vec2 Q;
    Q.x = M;
    Q.y = P;
    highp vec2 T;
    T = (texCoord + Q);
    highp float U;
    U = (90.0 + b);
    U = (((U - 180.0) * 3.14159) / 180.0);
    highp float V;
    V = cos(U);
    mediump float wr;
    wr = (W * V);
    highp float rr;
    rr = sin(U);
    mediump float yr;
    yr = (W * rr);
    mediump vec2 gr;
    gr.x = wr;
    gr.y = yr;
    highp vec2 xr;
    xr = (texCoord + gr);
    highp float br;
    br = (135.0 + b);
    br = (((br - 180.0) * 3.14159) / 180.0);
    highp float zr;
    zr = cos(br);
    mediump float ar;
    ar = (W * zr);
    highp float _r;
    _r = sin(br);
    mediump float Ar;
    Ar = (W * _r);
    mediump vec2 Yr;
    Yr.x = ar;
    Yr.y = Ar;
    highp vec2 Rr;
    Rr = (texCoord + Yr);
    highp float Xr;
    Xr = (180.0 + b);
    Xr = (((Xr - 180.0) * 3.14159) / 180.0);
    highp float Wr;
    Wr = cos(Xr);
    mediump float Zr;
    Zr = (W * Wr);
    highp float or;
    or = sin(Xr);
    mediump float Or;
    Or = (W * or);
    mediump vec2 Ir;
    Ir.x = Zr;
    Ir.y = Or;
    highp vec2 ir;
    ir = (texCoord + Ir);
    lowp float lr;
    lr = (1.0 - ((texture2D (blurmap0, T).w * texture2D (blurmap0, xr).w) * (texture2D (blurmap0, Rr).w * texture2D (blurmap0, ir).w)));
    _ = lr;
    highp float Sr;
    Sr = clamp (mix ((
      (z * 10.0)
     * Y), (
      (z * 10.0)
     * 
      pow (Y, 4.0)
    ), (1.0 - z)), 0.0, 1.0);
    Y = Sr;
    highp float Gr;
    Gr = clamp (mix ((
      (z * 10.0)
     * A), (
      (z * 10.0)
     * 
      pow (A, 4.0)
    ), (1.0 - z)), 0.0, 1.0);
    A = Gr;
    highp float Br;
    Br = clamp (mix ((
      (z * 10.0)
     * _), (
      (z * 10.0)
     * 
      pow (_, 4.0)
    ), (1.0 - z)), 0.0, 1.0);
    _ = Br;
    R = (0.8 + (z * 0.15));
    a = ((1.0 - (0.5 * 
      (1.0 - (clamp ((Br - Sr), 0.0, 1.0) * (0.8 + (z * 0.15))))
    )) * ((
      (1.0 - clamp ((Gr - Sr), 0.0, 1.0))
     * R) + (1.0 - R)));
    highp vec3 cr;
    cr.x = a;
    cr.y = a;
    cr.z = a;
    g = cr;
    if ((lightDir.y > 0.5)) {
      r = (b + 180.0);
    };
    highp float dr;
    dr = (0.2 + x);
    highp float er;
    highp float fr;
    highp float hr;
    highp float jr;
    highp float kr;
    kr = ((6.0 + dr) / 2048.0);
    highp float mr;
    mr = ((6.0 + dr) / 2048.0);
    lowp float nr;
    nr = texture2D (blurmap0, texCoord).w;
    jr = nr;
    jr = (1.0 - ((jr * jr) * (jr * jr)));
    highp float pr;
    pr = (((r - 180.0) * 3.14159) / 180.0);
    highp float qr;
    qr = cos(pr);
    mediump float sr;
    sr = (kr * qr);
    highp float tr;
    tr = sin(pr);
    mediump float ur;
    ur = (kr * tr);
    mediump vec2 vr;
    vr.x = sr;
    vr.y = ur;
    highp vec2 Cr;
    Cr = (texCoord + vr);
    highp float Dr;
    Dr = (-45.0 + r);
    Dr = (((Dr - 180.0) * 3.14159) / 180.0);
    highp float Er;
    Er = cos(Dr);
    mediump float Fr;
    Fr = (kr * Er);
    highp float Hr;
    Hr = sin(Dr);
    mediump float Jr;
    Jr = (kr * Hr);
    mediump vec2 Kr;
    Kr.x = Fr;
    Kr.y = Jr;
    highp vec2 Lr;
    Lr = (texCoord + Kr);
    highp float Mr;
    Mr = (-90.0 + r);
    Mr = (((Mr - 180.0) * 3.14159) / 180.0);
    highp float Nr;
    Nr = cos(Mr);
    mediump float Pr;
    Pr = (kr * Nr);
    highp float Qr;
    Qr = sin(Mr);
    mediump float Tr;
    Tr = (kr * Qr);
    mediump vec2 Ur;
    Ur.x = Pr;
    Ur.y = Tr;
    highp vec2 Vr;
    Vr = (texCoord + Ur);
    highp float wy;
    wy = (-135.0 + r);
    wy = (((wy - 180.0) * 3.14159) / 180.0);
    highp float ry;
    ry = cos(wy);
    mediump float yy;
    yy = (kr * ry);
    highp float gy;
    gy = sin(wy);
    mediump float xy;
    xy = (kr * gy);
    mediump vec2 by;
    by.x = yy;
    by.y = xy;
    highp vec2 zy;
    zy = (texCoord + by);
    lowp float ay;
    ay = (1.0 - ((texture2D (blurmap0, Cr).w * texture2D (blurmap0, Lr).w) * (texture2D (blurmap0, Vr).w * texture2D (blurmap0, zy).w)));
    hr = ay;
    highp float _y;
    _y = (45.0 + r);
    _y = (((_y - 180.0) * 3.14159) / 180.0);
    highp float Ay;
    Ay = cos(_y);
    mediump float Yy;
    Yy = (mr * Ay);
    highp float Ry;
    Ry = sin(_y);
    mediump float Xy;
    Xy = (mr * Ry);
    mediump vec2 Wy;
    Wy.x = Yy;
    Wy.y = Xy;
    highp vec2 Zy;
    Zy = (texCoord + Wy);
    highp float oy;
    oy = (90.0 + r);
    oy = (((oy - 180.0) * 3.14159) / 180.0);
    highp float Oy;
    Oy = cos(oy);
    mediump float Iy;
    Iy = (mr * Oy);
    highp float iy;
    iy = sin(oy);
    mediump float ly;
    ly = (mr * iy);
    mediump vec2 Sy;
    Sy.x = Iy;
    Sy.y = ly;
    highp vec2 Gy;
    Gy = (texCoord + Sy);
    highp float By;
    By = (135.0 + r);
    By = (((By - 180.0) * 3.14159) / 180.0);
    highp float cy;
    cy = cos(By);
    mediump float dy;
    dy = (mr * cy);
    highp float ey;
    ey = sin(By);
    mediump float fy;
    fy = (mr * ey);
    mediump vec2 hy;
    hy.x = dy;
    hy.y = fy;
    highp vec2 jy;
    jy = (texCoord + hy);
    highp float ky;
    ky = (180.0 + r);
    ky = (((ky - 180.0) * 3.14159) / 180.0);
    highp float my;
    my = cos(ky);
    mediump float ny;
    ny = (mr * my);
    highp float py;
    py = sin(ky);
    mediump float qy;
    qy = (mr * py);
    mediump vec2 sy;
    sy.x = ny;
    sy.y = qy;
    highp vec2 ty;
    ty = (texCoord + sy);
    lowp float uy;
    uy = (1.0 - ((texture2D (blurmap0, Zy).w * texture2D (blurmap0, Gy).w) * (texture2D (blurmap0, jy).w * texture2D (blurmap0, ty).w)));
    fr = uy;
    highp float vy;
    vy = clamp (mix ((
      (dr * 10.0)
     * jr), (
      (dr * 10.0)
     * 
      pow (jr, 4.0)
    ), (1.0 - dr)), 0.0, 1.0);
    jr = vy;
    highp float Cy;
    Cy = clamp (mix ((
      (dr * 10.0)
     * hr), (
      (dr * 10.0)
     * 
      pow (hr, 4.0)
    ), (1.0 - dr)), 0.0, 1.0);
    hr = Cy;
    highp float Dy;
    Dy = clamp (mix ((
      (dr * 10.0)
     * fr), (
      (dr * 10.0)
     * 
      pow (fr, 4.0)
    ), (1.0 - dr)), 0.0, 1.0);
    fr = Dy;
    er = (((1.0 - 
      ((0.5 * (1.0 - (
        clamp ((vy - Dy), 0.0, 1.0)
       * 0.4))) * (1.0 - (clamp (
        (Dy - vy)
      , 0.0, 1.0) * 0.4)))
    ) * (
      ((1.0 - clamp ((Cy - vy), 0.0, 1.0)) * 0.4)
     + 0.6)) * ((
      (1.0 - clamp ((vy - Cy), 0.0, 1.0))
     * 0.4) + 0.6));
    highp vec3 Ey;
    Ey.x = er;
    Ey.y = er;
    Ey.z = er;
    y = Ey;
  } else {
    if ((randomOffset.x < 0.5)) {
      if ((lightDir.y > 0.5)) {
        highp float Fy;
        Fy = (r + 180.0);
        highp float Hy;
        highp float Jy;
        highp float Ky;
        highp float Ly;
        lowp float My;
        lowp vec4 Ny;
        Ny = texture2D (blurmap0, texCoord);
        My = Ny.w;
        Ly = My;
        Ly = (1.0 - ((Ly * Ly) * (Ly * Ly)));
        highp float Py;
        Py = (((Fy - 180.0) * 3.14159) / 180.0);
        highp float Qy;
        Qy = cos(Py);
        mediump float Ty;
        Ty = (0.003320313 * Qy);
        highp float Uy;
        Uy = sin(Py);
        mediump float Vy;
        Vy = (0.003320313 * Uy);
        mediump vec2 wg;
        wg.x = Ty;
        wg.y = Vy;
        highp vec2 rg;
        rg = (texCoord + wg);
        highp float yg;
        yg = (-45.0 + Fy);
        yg = (((yg - 180.0) * 3.14159) / 180.0);
        highp float gg;
        gg = cos(yg);
        mediump float xg;
        xg = (0.003320313 * gg);
        highp float bg;
        bg = sin(yg);
        mediump float zg;
        zg = (0.003320313 * bg);
        mediump vec2 ag;
        ag.x = xg;
        ag.y = zg;
        highp vec2 _g;
        _g = (texCoord + ag);
        highp float Ag;
        Ag = (-90.0 + Fy);
        Ag = (((Ag - 180.0) * 3.14159) / 180.0);
        highp float Yg;
        Yg = cos(Ag);
        mediump float Rg;
        Rg = (0.003320313 * Yg);
        highp float Xg;
        Xg = sin(Ag);
        mediump float Wg;
        Wg = (0.003320313 * Xg);
        mediump vec2 Zg;
        Zg.x = Rg;
        Zg.y = Wg;
        highp vec2 og;
        og = (texCoord + Zg);
        highp float Og;
        Og = (-135.0 + Fy);
        Og = (((Og - 180.0) * 3.14159) / 180.0);
        highp float Ig;
        Ig = cos(Og);
        mediump float ig;
        ig = (0.003320313 * Ig);
        highp float lg;
        lg = sin(Og);
        mediump float Sg;
        Sg = (0.003320313 * lg);
        mediump vec2 Gg;
        Gg.x = ig;
        Gg.y = Sg;
        highp vec2 Bg;
        Bg = (texCoord + Gg);
        lowp float cg;
        cg = (1.0 - ((texture2D (blurmap0, rg).w * texture2D (blurmap0, _g).w) * (texture2D (blurmap0, og).w * texture2D (blurmap0, Bg).w)));
        Ky = cg;
        highp float dg;
        dg = (45.0 + Fy);
        dg = (((dg - 180.0) * 3.14159) / 180.0);
        highp float eg;
        eg = cos(dg);
        mediump float fg;
        fg = (0.003320313 * eg);
        highp float hg;
        hg = sin(dg);
        mediump float jg;
        jg = (0.003320313 * hg);
        mediump vec2 kg;
        kg.x = fg;
        kg.y = jg;
        highp vec2 mg;
        mg = (texCoord + kg);
        highp float ng;
        ng = (90.0 + Fy);
        ng = (((ng - 180.0) * 3.14159) / 180.0);
        highp float pg;
        pg = cos(ng);
        mediump float qg;
        qg = (0.003320313 * pg);
        highp float sg;
        sg = sin(ng);
        mediump float tg;
        tg = (0.003320313 * sg);
        mediump vec2 ug;
        ug.x = qg;
        ug.y = tg;
        highp vec2 vg;
        vg = (texCoord + ug);
        highp float Cg;
        Cg = (135.0 + Fy);
        Cg = (((Cg - 180.0) * 3.14159) / 180.0);
        highp float Dg;
        Dg = cos(Cg);
        mediump float Eg;
        Eg = (0.003320313 * Dg);
        highp float Fg;
        Fg = sin(Cg);
        mediump float Hg;
        Hg = (0.003320313 * Fg);
        mediump vec2 Jg;
        Jg.x = Eg;
        Jg.y = Hg;
        highp vec2 Kg;
        Kg = (texCoord + Jg);
        highp float Lg;
        Lg = (180.0 + Fy);
        Lg = (((Lg - 180.0) * 3.14159) / 180.0);
        highp float Mg;
        Mg = cos(Lg);
        mediump float Ng;
        Ng = (0.003320313 * Mg);
        highp float Pg;
        Pg = sin(Lg);
        mediump float Qg;
        Qg = (0.003320313 * Pg);
        mediump vec2 Tg;
        Tg.x = Ng;
        Tg.y = Qg;
        highp vec2 Ug;
        Ug = (texCoord + Tg);
        lowp float Vg;
        Vg = (1.0 - ((texture2D (blurmap0, mg).w * texture2D (blurmap0, vg).w) * (texture2D (blurmap0, Kg).w * texture2D (blurmap0, Ug).w)));
        Jy = Vg;
        highp float wx;
        wx = clamp (mix ((8.0 * Ly), (8.0 * 
          pow (Ly, 4.0)
        ), 0.2), 0.0, 1.0);
        Ly = wx;
        highp float rx;
        rx = clamp (mix ((8.0 * Ky), (8.0 * 
          pow (Ky, 4.0)
        ), 0.2), 0.0, 1.0);
        Ky = rx;
        highp float yx;
        yx = clamp (mix ((8.0 * Jy), (8.0 * 
          pow (Jy, 4.0)
        ), 0.2), 0.0, 1.0);
        Jy = yx;
        Hy = ((1.0 - (0.5 * 
          (1.0 - (clamp ((yx - wx), 0.0, 1.0) * 0.92))
        )) * ((
          (1.0 - clamp ((rx - wx), 0.0, 1.0))
         * 0.92) + 0.07999998));
        highp vec3 gx;
        gx.x = Hy;
        gx.y = Hy;
        gx.z = Hy;
        g = gx;
        highp float xx;
        highp float bx;
        highp float zx;
        highp float ax;
        lowp float _x;
        _x = Ny.w;
        ax = _x;
        ax = (1.0 - ((ax * ax) * (ax * ax)));
        highp float Ax;
        Ax = (((r - 180.0) * 3.14159) / 180.0);
        highp float Yx;
        Yx = cos(Ax);
        mediump float Rx;
        Rx = (0.003125 * Yx);
        highp float Xx;
        Xx = sin(Ax);
        mediump float Wx;
        Wx = (0.003125 * Xx);
        mediump vec2 Zx;
        Zx.x = Rx;
        Zx.y = Wx;
        highp vec2 ox;
        ox = (texCoord + Zx);
        highp float Ox;
        Ox = (-45.0 + r);
        Ox = (((Ox - 180.0) * 3.14159) / 180.0);
        highp float Ix;
        Ix = cos(Ox);
        mediump float ix;
        ix = (0.003125 * Ix);
        highp float lx;
        lx = sin(Ox);
        mediump float Sx;
        Sx = (0.003125 * lx);
        mediump vec2 Gx;
        Gx.x = ix;
        Gx.y = Sx;
        highp vec2 Bx;
        Bx = (texCoord + Gx);
        highp float cx;
        cx = (-90.0 + r);
        cx = (((cx - 180.0) * 3.14159) / 180.0);
        highp float dx;
        dx = cos(cx);
        mediump float ex;
        ex = (0.003125 * dx);
        highp float fx;
        fx = sin(cx);
        mediump float hx;
        hx = (0.003125 * fx);
        mediump vec2 jx;
        jx.x = ex;
        jx.y = hx;
        highp vec2 kx;
        kx = (texCoord + jx);
        highp float mx;
        mx = (-135.0 + r);
        mx = (((mx - 180.0) * 3.14159) / 180.0);
        highp float nx;
        nx = cos(mx);
        mediump float px;
        px = (0.003125 * nx);
        highp float qx;
        qx = sin(mx);
        mediump float sx;
        sx = (0.003125 * qx);
        mediump vec2 tx;
        tx.x = px;
        tx.y = sx;
        highp vec2 ux;
        ux = (texCoord + tx);
        lowp float vx;
        vx = (1.0 - ((texture2D (blurmap0, ox).w * texture2D (blurmap0, Bx).w) * (texture2D (blurmap0, kx).w * texture2D (blurmap0, ux).w)));
        zx = vx;
        highp float Cx;
        Cx = (45.0 + r);
        Cx = (((Cx - 180.0) * 3.14159) / 180.0);
        highp float Dx;
        Dx = cos(Cx);
        mediump float Ex;
        Ex = (0.003125 * Dx);
        highp float Fx;
        Fx = sin(Cx);
        mediump float Hx;
        Hx = (0.003125 * Fx);
        mediump vec2 Jx;
        Jx.x = Ex;
        Jx.y = Hx;
        highp vec2 Kx;
        Kx = (texCoord + Jx);
        highp float Lx;
        Lx = (90.0 + r);
        Lx = (((Lx - 180.0) * 3.14159) / 180.0);
        highp float Mx;
        Mx = cos(Lx);
        mediump float Nx;
        Nx = (0.003125 * Mx);
        highp float Px;
        Px = sin(Lx);
        mediump float Qx;
        Qx = (0.003125 * Px);
        mediump vec2 Tx;
        Tx.x = Nx;
        Tx.y = Qx;
        highp vec2 Ux;
        Ux = (texCoord + Tx);
        highp float Vx;
        Vx = (135.0 + r);
        Vx = (((Vx - 180.0) * 3.14159) / 180.0);
        highp float wb;
        wb = cos(Vx);
        mediump float rb;
        rb = (0.003125 * wb);
        highp float yb;
        yb = sin(Vx);
        mediump float gb;
        gb = (0.003125 * yb);
        mediump vec2 xb;
        xb.x = rb;
        xb.y = gb;
        highp vec2 bb;
        bb = (texCoord + xb);
        highp float zb;
        zb = (180.0 + r);
        zb = (((zb - 180.0) * 3.14159) / 180.0);
        highp float ab;
        ab = cos(zb);
        mediump float _b;
        _b = (0.003125 * ab);
        highp float Ab;
        Ab = sin(zb);
        mediump float Yb;
        Yb = (0.003125 * Ab);
        mediump vec2 Rb;
        Rb.x = _b;
        Rb.y = Yb;
        highp vec2 Xb;
        Xb = (texCoord + Rb);
        lowp float Wb;
        Wb = (1.0 - ((texture2D (blurmap0, Kx).w * texture2D (blurmap0, Ux).w) * (texture2D (blurmap0, bb).w * texture2D (blurmap0, Xb).w)));
        bx = Wb;
        highp float Zb;
        Zb = clamp (mix ((4.0 * ax), (4.0 * 
          pow (ax, 4.0)
        ), 0.6), 0.0, 1.0);
        ax = Zb;
        highp float ob;
        ob = clamp (mix ((4.0 * zx), (4.0 * 
          pow (zx, 4.0)
        ), 0.6), 0.0, 1.0);
        zx = ob;
        highp float Ob;
        Ob = clamp (mix ((4.0 * bx), (4.0 * 
          pow (bx, 4.0)
        ), 0.6), 0.0, 1.0);
        bx = Ob;
        xx = (((1.0 - 
          ((0.5 * (1.0 - (
            clamp ((Zb - Ob), 0.0, 1.0)
           * 0.4))) * (1.0 - (clamp (
            (Ob - Zb)
          , 0.0, 1.0) * 0.4)))
        ) * (
          ((1.0 - clamp ((ob - Zb), 0.0, 1.0)) * 0.4)
         + 0.6)) * ((
          (1.0 - clamp ((Zb - ob), 0.0, 1.0))
         * 0.4) + 0.6));
        highp vec3 Ib;
        Ib.x = xx;
        Ib.y = xx;
        Ib.z = xx;
        y = Ib;
      } else {
        highp float ib;
        ib = (r + 180.0);
        highp float lb;
        highp float Sb;
        highp float Gb;
        highp float Bb;
        lowp float cb;
        lowp vec4 db;
        db = texture2D (blurmap0, texCoord);
        cb = db.w;
        Bb = cb;
        Bb = (1.0 - ((Bb * Bb) * (Bb * Bb)));
        highp float eb;
        eb = (((ib - 180.0) * 3.14159) / 180.0);
        highp float fb;
        fb = cos(eb);
        mediump float hb;
        hb = (0.003320313 * fb);
        highp float jb;
        jb = sin(eb);
        mediump float kb;
        kb = (0.003320313 * jb);
        mediump vec2 mb;
        mb.x = hb;
        mb.y = kb;
        highp vec2 nb;
        nb = (texCoord + mb);
        highp float pb;
        pb = (-45.0 + ib);
        pb = (((pb - 180.0) * 3.14159) / 180.0);
        highp float qb;
        qb = cos(pb);
        mediump float sb;
        sb = (0.003320313 * qb);
        highp float tb;
        tb = sin(pb);
        mediump float ub;
        ub = (0.003320313 * tb);
        mediump vec2 vb;
        vb.x = sb;
        vb.y = ub;
        highp vec2 Cb;
        Cb = (texCoord + vb);
        highp float Db;
        Db = (-90.0 + ib);
        Db = (((Db - 180.0) * 3.14159) / 180.0);
        highp float Eb;
        Eb = cos(Db);
        mediump float Fb;
        Fb = (0.003320313 * Eb);
        highp float Hb;
        Hb = sin(Db);
        mediump float Jb;
        Jb = (0.003320313 * Hb);
        mediump vec2 Kb;
        Kb.x = Fb;
        Kb.y = Jb;
        highp vec2 Lb;
        Lb = (texCoord + Kb);
        highp float Mb;
        Mb = (-135.0 + ib);
        Mb = (((Mb - 180.0) * 3.14159) / 180.0);
        highp float Nb;
        Nb = cos(Mb);
        mediump float Pb;
        Pb = (0.003320313 * Nb);
        highp float Qb;
        Qb = sin(Mb);
        mediump float Tb;
        Tb = (0.003320313 * Qb);
        mediump vec2 Ub;
        Ub.x = Pb;
        Ub.y = Tb;
        highp vec2 Vb;
        Vb = (texCoord + Ub);
        lowp float wz;
        wz = (1.0 - ((texture2D (blurmap0, nb).w * texture2D (blurmap0, Cb).w) * (texture2D (blurmap0, Lb).w * texture2D (blurmap0, Vb).w)));
        Gb = wz;
        highp float rz;
        rz = (45.0 + ib);
        rz = (((rz - 180.0) * 3.14159) / 180.0);
        highp float yz;
        yz = cos(rz);
        mediump float gz;
        gz = (0.003320313 * yz);
        highp float xz;
        xz = sin(rz);
        mediump float bz;
        bz = (0.003320313 * xz);
        mediump vec2 zz;
        zz.x = gz;
        zz.y = bz;
        highp vec2 az;
        az = (texCoord + zz);
        highp float _z;
        _z = (90.0 + ib);
        _z = (((_z - 180.0) * 3.14159) / 180.0);
        highp float Az;
        Az = cos(_z);
        mediump float Yz;
        Yz = (0.003320313 * Az);
        highp float Rz;
        Rz = sin(_z);
        mediump float Xz;
        Xz = (0.003320313 * Rz);
        mediump vec2 Wz;
        Wz.x = Yz;
        Wz.y = Xz;
        highp vec2 Zz;
        Zz = (texCoord + Wz);
        highp float oz;
        oz = (135.0 + ib);
        oz = (((oz - 180.0) * 3.14159) / 180.0);
        highp float Oz;
        Oz = cos(oz);
        mediump float Iz;
        Iz = (0.003320313 * Oz);
        highp float iz;
        iz = sin(oz);
        mediump float lz;
        lz = (0.003320313 * iz);
        mediump vec2 Sz;
        Sz.x = Iz;
        Sz.y = lz;
        highp vec2 Gz;
        Gz = (texCoord + Sz);
        highp float Bz;
        Bz = (180.0 + ib);
        Bz = (((Bz - 180.0) * 3.14159) / 180.0);
        highp float cz;
        cz = cos(Bz);
        mediump float dz;
        dz = (0.003320313 * cz);
        highp float ez;
        ez = sin(Bz);
        mediump float fz;
        fz = (0.003320313 * ez);
        mediump vec2 hz;
        hz.x = dz;
        hz.y = fz;
        highp vec2 jz;
        jz = (texCoord + hz);
        lowp float kz;
        kz = (1.0 - ((texture2D (blurmap0, az).w * texture2D (blurmap0, Zz).w) * (texture2D (blurmap0, Gz).w * texture2D (blurmap0, jz).w)));
        Sb = kz;
        highp float mz;
        mz = clamp (mix ((8.0 * Bb), (8.0 * 
          pow (Bb, 4.0)
        ), 0.2), 0.0, 1.0);
        Bb = mz;
        highp float nz;
        nz = clamp (mix ((8.0 * Gb), (8.0 * 
          pow (Gb, 4.0)
        ), 0.2), 0.0, 1.0);
        Gb = nz;
        highp float pz;
        pz = clamp (mix ((8.0 * Sb), (8.0 * 
          pow (Sb, 4.0)
        ), 0.2), 0.0, 1.0);
        Sb = pz;
        lb = ((1.0 - (0.5 * 
          (1.0 - (clamp ((pz - mz), 0.0, 1.0) * 0.52))
        )) * ((
          (1.0 - clamp ((nz - mz), 0.0, 1.0))
         * 0.52) + 0.48));
        highp vec3 qz;
        qz.x = lb;
        qz.y = lb;
        qz.z = lb;
        g = qz;
        highp float sz;
        sz = (r + 180.0);
        highp float tz;
        highp float uz;
        highp float vz;
        highp float Cz;
        lowp float Dz;
        Dz = db.w;
        Cz = Dz;
        Cz = (1.0 - ((Cz * Cz) * (Cz * Cz)));
        highp float Ez;
        Ez = (((sz - 180.0) * 3.14159) / 180.0);
        highp float Fz;
        Fz = cos(Ez);
        mediump float Hz;
        Hz = (0.003125 * Fz);
        highp float Jz;
        Jz = sin(Ez);
        mediump float Kz;
        Kz = (0.003125 * Jz);
        mediump vec2 Lz;
        Lz.x = Hz;
        Lz.y = Kz;
        highp vec2 Mz;
        Mz = (texCoord + Lz);
        highp float Nz;
        Nz = (-45.0 + sz);
        Nz = (((Nz - 180.0) * 3.14159) / 180.0);
        highp float Pz;
        Pz = cos(Nz);
        mediump float Qz;
        Qz = (0.003125 * Pz);
        highp float Tz;
        Tz = sin(Nz);
        mediump float Uz;
        Uz = (0.003125 * Tz);
        mediump vec2 Vz;
        Vz.x = Qz;
        Vz.y = Uz;
        highp vec2 wa;
        wa = (texCoord + Vz);
        highp float ra;
        ra = (-90.0 + sz);
        ra = (((ra - 180.0) * 3.14159) / 180.0);
        highp float ya;
        ya = cos(ra);
        mediump float ga;
        ga = (0.003125 * ya);
        highp float xa;
        xa = sin(ra);
        mediump float ba;
        ba = (0.003125 * xa);
        mediump vec2 za;
        za.x = ga;
        za.y = ba;
        highp vec2 aa;
        aa = (texCoord + za);
        highp float _a;
        _a = (-135.0 + sz);
        _a = (((_a - 180.0) * 3.14159) / 180.0);
        highp float Aa;
        Aa = cos(_a);
        mediump float Ya;
        Ya = (0.003125 * Aa);
        highp float Ra;
        Ra = sin(_a);
        mediump float Xa;
        Xa = (0.003125 * Ra);
        mediump vec2 Wa;
        Wa.x = Ya;
        Wa.y = Xa;
        highp vec2 Za;
        Za = (texCoord + Wa);
        lowp float oa;
        oa = (1.0 - ((texture2D (blurmap0, Mz).w * texture2D (blurmap0, wa).w) * (texture2D (blurmap0, aa).w * texture2D (blurmap0, Za).w)));
        vz = oa;
        highp float Oa;
        Oa = (45.0 + sz);
        Oa = (((Oa - 180.0) * 3.14159) / 180.0);
        highp float Ia;
        Ia = cos(Oa);
        mediump float ia;
        ia = (0.003125 * Ia);
        highp float la;
        la = sin(Oa);
        mediump float Sa;
        Sa = (0.003125 * la);
        mediump vec2 Ga;
        Ga.x = ia;
        Ga.y = Sa;
        highp vec2 Ba;
        Ba = (texCoord + Ga);
        highp float ca;
        ca = (90.0 + sz);
        ca = (((ca - 180.0) * 3.14159) / 180.0);
        highp float da;
        da = cos(ca);
        mediump float ea;
        ea = (0.003125 * da);
        highp float fa;
        fa = sin(ca);
        mediump float ha;
        ha = (0.003125 * fa);
        mediump vec2 ja;
        ja.x = ea;
        ja.y = ha;
        highp vec2 ka;
        ka = (texCoord + ja);
        highp float ma;
        ma = (135.0 + sz);
        ma = (((ma - 180.0) * 3.14159) / 180.0);
        highp float na;
        na = cos(ma);
        mediump float pa;
        pa = (0.003125 * na);
        highp float qa;
        qa = sin(ma);
        mediump float sa;
        sa = (0.003125 * qa);
        mediump vec2 ta;
        ta.x = pa;
        ta.y = sa;
        highp vec2 ua;
        ua = (texCoord + ta);
        highp float va;
        va = (180.0 + sz);
        va = (((va - 180.0) * 3.14159) / 180.0);
        highp float Ca;
        Ca = cos(va);
        mediump float Da;
        Da = (0.003125 * Ca);
        highp float Ea;
        Ea = sin(va);
        mediump float Fa;
        Fa = (0.003125 * Ea);
        mediump vec2 Ha;
        Ha.x = Da;
        Ha.y = Fa;
        highp vec2 Ja;
        Ja = (texCoord + Ha);
        lowp float Ka;
        Ka = (1.0 - ((texture2D (blurmap0, Ba).w * texture2D (blurmap0, ka).w) * (texture2D (blurmap0, ua).w * texture2D (blurmap0, Ja).w)));
        uz = Ka;
        highp float La;
        La = clamp (mix ((4.0 * Cz), (4.0 * 
          pow (Cz, 4.0)
        ), 0.6), 0.0, 1.0);
        Cz = La;
        highp float Ma;
        Ma = clamp (mix ((4.0 * vz), (4.0 * 
          pow (vz, 4.0)
        ), 0.6), 0.0, 1.0);
        vz = Ma;
        highp float Na;
        Na = clamp (mix ((4.0 * uz), (4.0 * 
          pow (uz, 4.0)
        ), 0.6), 0.0, 1.0);
        uz = Na;
        tz = (((1.0 - 
          ((0.5 * (1.0 - (
            clamp ((La - Na), 0.0, 1.0)
           * 0.4))) * (1.0 - (clamp (
            (Na - La)
          , 0.0, 1.0) * 0.4)))
        ) * (
          ((1.0 - clamp ((Ma - La), 0.0, 1.0)) * 0.4)
         + 0.6)) * ((
          (1.0 - clamp ((La - Ma), 0.0, 1.0))
         * 0.4) + 0.6));
        highp vec3 Pa;
        Pa.x = tz;
        Pa.y = tz;
        Pa.z = tz;
        y = Pa;
      };
    } else {
      if ((randomOffset.x < 0.75)) {
        x = (0.2 + (0.5 * lightDir.y));
        if ((randomOffset.y < 0.5)) {
          highp float Qa;
          highp float Ta;
          highp float Ua;
          highp float Va;
          highp float w_;
          highp float r_;
          r_ = ((6.0 + x) / 2048.0);
          highp float y_;
          y_ = ((6.0 + x) / 2048.0);
          lowp float g_;
          lowp vec4 x_;
          x_ = texture2D (blurmap0, texCoord);
          g_ = x_.w;
          Va = g_;
          Va = (1.0 - ((Va * Va) * (Va * Va)));
          highp float b_;
          b_ = (((r - 180.0) * 3.14159) / 180.0);
          highp float z_;
          z_ = cos(b_);
          mediump float a_;
          a_ = (r_ * z_);
          highp float __;
          __ = sin(b_);
          mediump float A_;
          A_ = (r_ * __);
          mediump vec2 Y_;
          Y_.x = a_;
          Y_.y = A_;
          highp vec2 R_;
          R_ = (texCoord + Y_);
          highp float X_;
          X_ = (-45.0 + r);
          X_ = (((X_ - 180.0) * 3.14159) / 180.0);
          highp float W_;
          W_ = cos(X_);
          mediump float Z_;
          Z_ = (r_ * W_);
          highp float o_;
          o_ = sin(X_);
          mediump float O_;
          O_ = (r_ * o_);
          mediump vec2 I_;
          I_.x = Z_;
          I_.y = O_;
          highp vec2 i_;
          i_ = (texCoord + I_);
          highp float l_;
          l_ = (-90.0 + r);
          l_ = (((l_ - 180.0) * 3.14159) / 180.0);
          highp float S_;
          S_ = cos(l_);
          mediump float G_;
          G_ = (r_ * S_);
          highp float B_;
          B_ = sin(l_);
          mediump float c_;
          c_ = (r_ * B_);
          mediump vec2 d_;
          d_.x = G_;
          d_.y = c_;
          highp vec2 e_;
          e_ = (texCoord + d_);
          highp float f_;
          f_ = (-135.0 + r);
          f_ = (((f_ - 180.0) * 3.14159) / 180.0);
          highp float h_;
          h_ = cos(f_);
          mediump float j_;
          j_ = (r_ * h_);
          highp float k_;
          k_ = sin(f_);
          mediump float m_;
          m_ = (r_ * k_);
          mediump vec2 n_;
          n_.x = j_;
          n_.y = m_;
          highp vec2 p_;
          p_ = (texCoord + n_);
          lowp float q_;
          q_ = (1.0 - ((texture2D (blurmap0, R_).w * texture2D (blurmap0, i_).w) * (texture2D (blurmap0, e_).w * texture2D (blurmap0, p_).w)));
          Ua = q_;
          highp float s_;
          s_ = (45.0 + r);
          s_ = (((s_ - 180.0) * 3.14159) / 180.0);
          highp float t_;
          t_ = cos(s_);
          mediump float u_;
          u_ = (y_ * t_);
          highp float v_;
          v_ = sin(s_);
          mediump float C_;
          C_ = (y_ * v_);
          mediump vec2 D_;
          D_.x = u_;
          D_.y = C_;
          highp vec2 E_;
          E_ = (texCoord + D_);
          highp float F_;
          F_ = (90.0 + r);
          F_ = (((F_ - 180.0) * 3.14159) / 180.0);
          highp float H_;
          H_ = cos(F_);
          mediump float J_;
          J_ = (y_ * H_);
          highp float K_;
          K_ = sin(F_);
          mediump float L_;
          L_ = (y_ * K_);
          mediump vec2 M_;
          M_.x = J_;
          M_.y = L_;
          highp vec2 N_;
          N_ = (texCoord + M_);
          highp float P_;
          P_ = (135.0 + r);
          P_ = (((P_ - 180.0) * 3.14159) / 180.0);
          highp float Q_;
          Q_ = cos(P_);
          mediump float T_;
          T_ = (y_ * Q_);
          highp float U_;
          U_ = sin(P_);
          mediump float V_;
          V_ = (y_ * U_);
          mediump vec2 wA;
          wA.x = T_;
          wA.y = V_;
          highp vec2 rA;
          rA = (texCoord + wA);
          highp float yA;
          yA = (180.0 + r);
          yA = (((yA - 180.0) * 3.14159) / 180.0);
          highp float gA;
          gA = cos(yA);
          mediump float xA;
          xA = (y_ * gA);
          highp float bA;
          bA = sin(yA);
          mediump float zA;
          zA = (y_ * bA);
          mediump vec2 aA;
          aA.x = xA;
          aA.y = zA;
          highp vec2 _A;
          _A = (texCoord + aA);
          lowp float AA;
          AA = (1.0 - ((texture2D (blurmap0, E_).w * texture2D (blurmap0, N_).w) * (texture2D (blurmap0, rA).w * texture2D (blurmap0, _A).w)));
          Ta = AA;
          highp float YA;
          YA = clamp (mix ((
            (x * 10.0)
           * Va), (
            (x * 10.0)
           * 
            pow (Va, 4.0)
          ), (1.0 - x)), 0.0, 1.0);
          Va = YA;
          highp float RA;
          RA = clamp (mix ((
            (x * 10.0)
           * Ua), (
            (x * 10.0)
           * 
            pow (Ua, 4.0)
          ), (1.0 - x)), 0.0, 1.0);
          Ua = RA;
          highp float XA;
          XA = clamp (mix ((
            (x * 10.0)
           * Ta), (
            (x * 10.0)
           * 
            pow (Ta, 4.0)
          ), (1.0 - x)), 0.0, 1.0);
          Ta = XA;
          w_ = (0.5 + (x * 0.15));
          Qa = ((1.0 - (0.5 * 
            (1.0 - (clamp ((XA - YA), 0.0, 1.0) * (0.5 + (x * 0.15))))
          )) * ((
            (1.0 - clamp ((RA - YA), 0.0, 1.0))
           * w_) + (1.0 - w_)));
          highp vec3 WA;
          WA.x = Qa;
          WA.y = Qa;
          WA.z = Qa;
          g = WA;
          highp float ZA;
          highp float oA;
          highp float OA;
          highp float IA;
          highp float iA;
          iA = ((6.0 + x) / 2048.0);
          highp float lA;
          lA = ((6.0 + x) / 2048.0);
          lowp float SA;
          SA = x_.w;
          IA = SA;
          IA = (1.0 - ((IA * IA) * (IA * IA)));
          highp float GA;
          GA = (((r - 180.0) * 3.14159) / 180.0);
          highp float BA;
          BA = cos(GA);
          mediump float cA;
          cA = (iA * BA);
          highp float dA;
          dA = sin(GA);
          mediump float eA;
          eA = (iA * dA);
          mediump vec2 fA;
          fA.x = cA;
          fA.y = eA;
          highp vec2 hA;
          hA = (texCoord + fA);
          highp float jA;
          jA = (-45.0 + r);
          jA = (((jA - 180.0) * 3.14159) / 180.0);
          highp float kA;
          kA = cos(jA);
          mediump float mA;
          mA = (iA * kA);
          highp float nA;
          nA = sin(jA);
          mediump float pA;
          pA = (iA * nA);
          mediump vec2 qA;
          qA.x = mA;
          qA.y = pA;
          highp vec2 sA;
          sA = (texCoord + qA);
          highp float tA;
          tA = (-90.0 + r);
          tA = (((tA - 180.0) * 3.14159) / 180.0);
          highp float uA;
          uA = cos(tA);
          mediump float vA;
          vA = (iA * uA);
          highp float CA;
          CA = sin(tA);
          mediump float DA;
          DA = (iA * CA);
          mediump vec2 EA;
          EA.x = vA;
          EA.y = DA;
          highp vec2 FA;
          FA = (texCoord + EA);
          highp float HA;
          HA = (-135.0 + r);
          HA = (((HA - 180.0) * 3.14159) / 180.0);
          highp float JA;
          JA = cos(HA);
          mediump float KA;
          KA = (iA * JA);
          highp float LA;
          LA = sin(HA);
          mediump float MA;
          MA = (iA * LA);
          mediump vec2 NA;
          NA.x = KA;
          NA.y = MA;
          highp vec2 PA;
          PA = (texCoord + NA);
          lowp float QA;
          QA = (1.0 - ((texture2D (blurmap0, hA).w * texture2D (blurmap0, sA).w) * (texture2D (blurmap0, FA).w * texture2D (blurmap0, PA).w)));
          OA = QA;
          highp float TA;
          TA = (45.0 + r);
          TA = (((TA - 180.0) * 3.14159) / 180.0);
          highp float UA;
          UA = cos(TA);
          mediump float VA;
          VA = (lA * UA);
          highp float wY;
          wY = sin(TA);
          mediump float rY;
          rY = (lA * wY);
          mediump vec2 yY;
          yY.x = VA;
          yY.y = rY;
          highp vec2 gY;
          gY = (texCoord + yY);
          highp float xY;
          xY = (90.0 + r);
          xY = (((xY - 180.0) * 3.14159) / 180.0);
          highp float bY;
          bY = cos(xY);
          mediump float zY;
          zY = (lA * bY);
          highp float aY;
          aY = sin(xY);
          mediump float _Y;
          _Y = (lA * aY);
          mediump vec2 AY;
          AY.x = zY;
          AY.y = _Y;
          highp vec2 YY;
          YY = (texCoord + AY);
          highp float RY;
          RY = (135.0 + r);
          RY = (((RY - 180.0) * 3.14159) / 180.0);
          highp float XY;
          XY = cos(RY);
          mediump float WY;
          WY = (lA * XY);
          highp float ZY;
          ZY = sin(RY);
          mediump float oY;
          oY = (lA * ZY);
          mediump vec2 OY;
          OY.x = WY;
          OY.y = oY;
          highp vec2 IY;
          IY = (texCoord + OY);
          highp float iY;
          iY = (180.0 + r);
          iY = (((iY - 180.0) * 3.14159) / 180.0);
          highp float lY;
          lY = cos(iY);
          mediump float SY;
          SY = (lA * lY);
          highp float GY;
          GY = sin(iY);
          mediump float BY;
          BY = (lA * GY);
          mediump vec2 cY;
          cY.x = SY;
          cY.y = BY;
          highp vec2 dY;
          dY = (texCoord + cY);
          lowp float eY;
          eY = (1.0 - ((texture2D (blurmap0, gY).w * texture2D (blurmap0, YY).w) * (texture2D (blurmap0, IY).w * texture2D (blurmap0, dY).w)));
          oA = eY;
          highp float fY;
          fY = clamp (mix ((
            (x * 10.0)
           * IA), (
            (x * 10.0)
           * 
            pow (IA, 4.0)
          ), (1.0 - x)), 0.0, 1.0);
          IA = fY;
          highp float hY;
          hY = clamp (mix ((
            (x * 10.0)
           * OA), (
            (x * 10.0)
           * 
            pow (OA, 4.0)
          ), (1.0 - x)), 0.0, 1.0);
          OA = hY;
          highp float jY;
          jY = clamp (mix ((
            (x * 10.0)
           * oA), (
            (x * 10.0)
           * 
            pow (oA, 4.0)
          ), (1.0 - x)), 0.0, 1.0);
          oA = jY;
          ZA = (((1.0 - 
            ((0.5 * (1.0 - (
              clamp ((fY - jY), 0.0, 1.0)
             * 0.2))) * (1.0 - (clamp (
              (jY - fY)
            , 0.0, 1.0) * 0.2)))
          ) * (
            ((1.0 - clamp ((hY - fY), 0.0, 1.0)) * 0.2)
           + 0.8)) * ((
            (1.0 - clamp ((fY - hY), 0.0, 1.0))
           * 0.2) + 0.8));
          highp vec3 kY;
          kY.x = ZA;
          kY.y = ZA;
          kY.z = ZA;
          y = kY;
        } else {
          highp float mY;
          highp float nY;
          highp float pY;
          highp float qY;
          highp float sY;
          highp float tY;
          tY = ((5.0 + x) / 2048.0);
          highp float uY;
          uY = ((5.0 + x) / 2048.0);
          lowp float vY;
          vY = texture2D (blurmap0, texCoord).w;
          qY = vY;
          qY = (1.0 - ((qY * qY) * (qY * qY)));
          highp float CY;
          CY = (((r - 180.0) * 3.14159) / 180.0);
          highp float DY;
          DY = cos(CY);
          mediump float EY;
          EY = (tY * DY);
          highp float FY;
          FY = sin(CY);
          mediump float HY;
          HY = (tY * FY);
          mediump vec2 JY;
          JY.x = EY;
          JY.y = HY;
          highp vec2 KY;
          KY = (texCoord + JY);
          highp float LY;
          LY = (-45.0 + r);
          LY = (((LY - 180.0) * 3.14159) / 180.0);
          highp float MY;
          MY = cos(LY);
          mediump float NY;
          NY = (tY * MY);
          highp float PY;
          PY = sin(LY);
          mediump float QY;
          QY = (tY * PY);
          mediump vec2 TY;
          TY.x = NY;
          TY.y = QY;
          highp vec2 UY;
          UY = (texCoord + TY);
          highp float VY;
          VY = (-90.0 + r);
          VY = (((VY - 180.0) * 3.14159) / 180.0);
          highp float wR;
          wR = cos(VY);
          mediump float rR;
          rR = (tY * wR);
          highp float yR;
          yR = sin(VY);
          mediump float gR;
          gR = (tY * yR);
          mediump vec2 xR;
          xR.x = rR;
          xR.y = gR;
          highp vec2 bR;
          bR = (texCoord + xR);
          highp float zR;
          zR = (-135.0 + r);
          zR = (((zR - 180.0) * 3.14159) / 180.0);
          highp float aR;
          aR = cos(zR);
          mediump float _R;
          _R = (tY * aR);
          highp float AR;
          AR = sin(zR);
          mediump float YR;
          YR = (tY * AR);
          mediump vec2 RR;
          RR.x = _R;
          RR.y = YR;
          highp vec2 XR;
          XR = (texCoord + RR);
          lowp float WR;
          WR = (1.0 - ((texture2D (blurmap0, KY).w * texture2D (blurmap0, UY).w) * (texture2D (blurmap0, bR).w * texture2D (blurmap0, XR).w)));
          pY = WR;
          highp float ZR;
          ZR = (45.0 + r);
          ZR = (((ZR - 180.0) * 3.14159) / 180.0);
          highp float oR;
          oR = cos(ZR);
          mediump float OR;
          OR = (uY * oR);
          highp float IR;
          IR = sin(ZR);
          mediump float iR;
          iR = (uY * IR);
          mediump vec2 lR;
          lR.x = OR;
          lR.y = iR;
          highp vec2 SR;
          SR = (texCoord + lR);
          highp float GR;
          GR = (90.0 + r);
          GR = (((GR - 180.0) * 3.14159) / 180.0);
          highp float BR;
          BR = cos(GR);
          mediump float cR;
          cR = (uY * BR);
          highp float dR;
          dR = sin(GR);
          mediump float eR;
          eR = (uY * dR);
          mediump vec2 fR;
          fR.x = cR;
          fR.y = eR;
          highp vec2 hR;
          hR = (texCoord + fR);
          highp float jR;
          jR = (135.0 + r);
          jR = (((jR - 180.0) * 3.14159) / 180.0);
          highp float kR;
          kR = cos(jR);
          mediump float mR;
          mR = (uY * kR);
          highp float nR;
          nR = sin(jR);
          mediump float pR;
          pR = (uY * nR);
          mediump vec2 qR;
          qR.x = mR;
          qR.y = pR;
          highp vec2 sR;
          sR = (texCoord + qR);
          highp float tR;
          tR = (180.0 + r);
          tR = (((tR - 180.0) * 3.14159) / 180.0);
          highp float uR;
          uR = cos(tR);
          mediump float vR;
          vR = (uY * uR);
          highp float CR;
          CR = sin(tR);
          mediump float DR;
          DR = (uY * CR);
          mediump vec2 ER;
          ER.x = vR;
          ER.y = DR;
          highp vec2 FR;
          FR = (texCoord + ER);
          lowp float HR;
          HR = (1.0 - ((texture2D (blurmap0, SR).w * texture2D (blurmap0, hR).w) * (texture2D (blurmap0, sR).w * texture2D (blurmap0, FR).w)));
          nY = HR;
          highp float JR;
          JR = clamp (mix ((
            (x * 10.0)
           * qY), (
            (x * 10.0)
           * 
            pow (qY, 4.0)
          ), (1.0 - x)), 0.0, 1.0);
          qY = JR;
          highp float KR;
          KR = clamp (mix ((
            (x * 10.0)
           * pY), (
            (x * 10.0)
           * 
            pow (pY, 4.0)
          ), (1.0 - x)), 0.0, 1.0);
          pY = KR;
          highp float LR;
          LR = clamp (mix ((
            (x * 10.0)
           * nY), (
            (x * 10.0)
           * 
            pow (nY, 4.0)
          ), (1.0 - x)), 0.0, 1.0);
          nY = LR;
          sY = (0.9 + (x * 0.15));
          mY = ((1.0 - (0.5 * 
            (1.0 - (clamp ((LR - JR), 0.0, 1.0) * (0.9 + (x * 0.15))))
          )) * ((
            (1.0 - clamp ((KR - JR), 0.0, 1.0))
           * sY) + (1.0 - sY)));
          highp vec3 MR;
          MR.x = mY;
          MR.y = mY;
          MR.z = mY;
          g = MR;
          y = vec3(0.5, 0.5, 0.5);
        };
      } else {
        x = (0.2 + (0.35 * lightDir.y));
        highp float NR;
        highp float PR;
        highp float QR;
        highp float TR;
        highp float UR;
        highp float VR;
        VR = ((12.0 + x) / 2048.0);
        highp float wX;
        wX = ((12.0 + x) / 2048.0);
        lowp float rX;
        lowp vec4 yX;
        yX = texture2D (blurmap0, texCoord);
        rX = yX.w;
        TR = rX;
        TR = (1.0 - ((TR * TR) * (TR * TR)));
        highp float gX;
        gX = (((r - 180.0) * 3.14159) / 180.0);
        highp float xX;
        xX = cos(gX);
        mediump float bX;
        bX = (VR * xX);
        highp float zX;
        zX = sin(gX);
        mediump float aX;
        aX = (VR * zX);
        mediump vec2 _X;
        _X.x = bX;
        _X.y = aX;
        highp vec2 AX;
        AX = (texCoord + _X);
        highp float YX;
        YX = (-45.0 + r);
        YX = (((YX - 180.0) * 3.14159) / 180.0);
        highp float RX;
        RX = cos(YX);
        mediump float XX;
        XX = (VR * RX);
        highp float WX;
        WX = sin(YX);
        mediump float ZX;
        ZX = (VR * WX);
        mediump vec2 oX;
        oX.x = XX;
        oX.y = ZX;
        highp vec2 OX;
        OX = (texCoord + oX);
        highp float IX;
        IX = (-90.0 + r);
        IX = (((IX - 180.0) * 3.14159) / 180.0);
        highp float iX;
        iX = cos(IX);
        mediump float lX;
        lX = (VR * iX);
        highp float SX;
        SX = sin(IX);
        mediump float GX;
        GX = (VR * SX);
        mediump vec2 BX;
        BX.x = lX;
        BX.y = GX;
        highp vec2 cX;
        cX = (texCoord + BX);
        highp float dX;
        dX = (-135.0 + r);
        dX = (((dX - 180.0) * 3.14159) / 180.0);
        highp float eX;
        eX = cos(dX);
        mediump float fX;
        fX = (VR * eX);
        highp float hX;
        hX = sin(dX);
        mediump float jX;
        jX = (VR * hX);
        mediump vec2 kX;
        kX.x = fX;
        kX.y = jX;
        highp vec2 mX;
        mX = (texCoord + kX);
        lowp float nX;
        nX = (1.0 - ((texture2D (blurmap0, AX).w * texture2D (blurmap0, OX).w) * (texture2D (blurmap0, cX).w * texture2D (blurmap0, mX).w)));
        QR = nX;
        highp float pX;
        pX = (45.0 + r);
        pX = (((pX - 180.0) * 3.14159) / 180.0);
        highp float qX;
        qX = cos(pX);
        mediump float sX;
        sX = (wX * qX);
        highp float tX;
        tX = sin(pX);
        mediump float uX;
        uX = (wX * tX);
        mediump vec2 vX;
        vX.x = sX;
        vX.y = uX;
        highp vec2 CX;
        CX = (texCoord + vX);
        highp float DX;
        DX = (90.0 + r);
        DX = (((DX - 180.0) * 3.14159) / 180.0);
        highp float EX;
        EX = cos(DX);
        mediump float FX;
        FX = (wX * EX);
        highp float HX;
        HX = sin(DX);
        mediump float JX;
        JX = (wX * HX);
        mediump vec2 KX;
        KX.x = FX;
        KX.y = JX;
        highp vec2 LX;
        LX = (texCoord + KX);
        highp float MX;
        MX = (135.0 + r);
        MX = (((MX - 180.0) * 3.14159) / 180.0);
        highp float NX;
        NX = cos(MX);
        mediump float PX;
        PX = (wX * NX);
        highp float QX;
        QX = sin(MX);
        mediump float TX;
        TX = (wX * QX);
        mediump vec2 UX;
        UX.x = PX;
        UX.y = TX;
        highp vec2 VX;
        VX = (texCoord + UX);
        highp float wW;
        wW = (180.0 + r);
        wW = (((wW - 180.0) * 3.14159) / 180.0);
        highp float rW;
        rW = cos(wW);
        mediump float yW;
        yW = (wX * rW);
        highp float gW;
        gW = sin(wW);
        mediump float xW;
        xW = (wX * gW);
        mediump vec2 bW;
        bW.x = yW;
        bW.y = xW;
        highp vec2 zW;
        zW = (texCoord + bW);
        lowp float aW;
        aW = (1.0 - ((texture2D (blurmap0, CX).w * texture2D (blurmap0, LX).w) * (texture2D (blurmap0, VX).w * texture2D (blurmap0, zW).w)));
        PR = aW;
        highp float _W;
        _W = clamp (mix ((
          (x * 10.0)
         * TR), (
          (x * 10.0)
         * 
          pow (TR, 4.0)
        ), (1.0 - x)), 0.0, 1.0);
        TR = _W;
        highp float AW;
        AW = clamp (mix ((
          (x * 10.0)
         * QR), (
          (x * 10.0)
         * 
          pow (QR, 4.0)
        ), (1.0 - x)), 0.0, 1.0);
        QR = AW;
        highp float YW;
        YW = clamp (mix ((
          (x * 10.0)
         * PR), (
          (x * 10.0)
         * 
          pow (PR, 4.0)
        ), (1.0 - x)), 0.0, 1.0);
        PR = YW;
        UR = (0.2 + (x * 0.15));
        NR = ((1.0 - (0.5 * 
          (1.0 - (clamp ((YW - _W), 0.0, 1.0) * (0.2 + (x * 0.15))))
        )) * ((
          (1.0 - clamp ((AW - _W), 0.0, 1.0))
         * UR) + (1.0 - UR)));
        highp vec3 RW;
        RW.x = NR;
        RW.y = NR;
        RW.z = NR;
        g = RW;
        highp float XW;
        highp float WW;
        highp float ZW;
        highp float oW;
        highp float OW;
        OW = ((6.0 + x) / 2048.0);
        highp float IW;
        IW = ((6.0 + x) / 2048.0);
        lowp float iW;
        iW = yX.w;
        oW = iW;
        oW = (1.0 - ((oW * oW) * (oW * oW)));
        highp float lW;
        lW = (((r - 180.0) * 3.14159) / 180.0);
        highp float SW;
        SW = cos(lW);
        mediump float GW;
        GW = (OW * SW);
        highp float BW;
        BW = sin(lW);
        mediump float cW;
        cW = (OW * BW);
        mediump vec2 dW;
        dW.x = GW;
        dW.y = cW;
        highp vec2 eW;
        eW = (texCoord + dW);
        highp float fW;
        fW = (-45.0 + r);
        fW = (((fW - 180.0) * 3.14159) / 180.0);
        highp float hW;
        hW = cos(fW);
        mediump float jW;
        jW = (OW * hW);
        highp float kW;
        kW = sin(fW);
        mediump float mW;
        mW = (OW * kW);
        mediump vec2 nW;
        nW.x = jW;
        nW.y = mW;
        highp vec2 pW;
        pW = (texCoord + nW);
        highp float qW;
        qW = (-90.0 + r);
        qW = (((qW - 180.0) * 3.14159) / 180.0);
        highp float sW;
        sW = cos(qW);
        mediump float tW;
        tW = (OW * sW);
        highp float uW;
        uW = sin(qW);
        mediump float vW;
        vW = (OW * uW);
        mediump vec2 CW;
        CW.x = tW;
        CW.y = vW;
        highp vec2 DW;
        DW = (texCoord + CW);
        highp float EW;
        EW = (-135.0 + r);
        EW = (((EW - 180.0) * 3.14159) / 180.0);
        highp float FW;
        FW = cos(EW);
        mediump float HW;
        HW = (OW * FW);
        highp float JW;
        JW = sin(EW);
        mediump float KW;
        KW = (OW * JW);
        mediump vec2 LW;
        LW.x = HW;
        LW.y = KW;
        highp vec2 MW;
        MW = (texCoord + LW);
        lowp float NW;
        NW = (1.0 - ((texture2D (blurmap0, eW).w * texture2D (blurmap0, pW).w) * (texture2D (blurmap0, DW).w * texture2D (blurmap0, MW).w)));
        ZW = NW;
        highp float PW;
        PW = (45.0 + r);
        PW = (((PW - 180.0) * 3.14159) / 180.0);
        highp float QW;
        QW = cos(PW);
        mediump float TW;
        TW = (IW * QW);
        highp float UW;
        UW = sin(PW);
        mediump float VW;
        VW = (IW * UW);
        mediump vec2 wZ;
        wZ.x = TW;
        wZ.y = VW;
        highp vec2 rZ;
        rZ = (texCoord + wZ);
        highp float yZ;
        yZ = (90.0 + r);
        yZ = (((yZ - 180.0) * 3.14159) / 180.0);
        highp float gZ;
        gZ = cos(yZ);
        mediump float xZ;
        xZ = (IW * gZ);
        highp float bZ;
        bZ = sin(yZ);
        mediump float zZ;
        zZ = (IW * bZ);
        mediump vec2 aZ;
        aZ.x = xZ;
        aZ.y = zZ;
        highp vec2 _Z;
        _Z = (texCoord + aZ);
        highp float AZ;
        AZ = (135.0 + r);
        AZ = (((AZ - 180.0) * 3.14159) / 180.0);
        highp float YZ;
        YZ = cos(AZ);
        mediump float RZ;
        RZ = (IW * YZ);
        highp float XZ;
        XZ = sin(AZ);
        mediump float WZ;
        WZ = (IW * XZ);
        mediump vec2 ZZ;
        ZZ.x = RZ;
        ZZ.y = WZ;
        highp vec2 oZ;
        oZ = (texCoord + ZZ);
        highp float OZ;
        OZ = (180.0 + r);
        OZ = (((OZ - 180.0) * 3.14159) / 180.0);
        highp float IZ;
        IZ = cos(OZ);
        mediump float iZ;
        iZ = (IW * IZ);
        highp float lZ;
        lZ = sin(OZ);
        mediump float SZ;
        SZ = (IW * lZ);
        mediump vec2 GZ;
        GZ.x = iZ;
        GZ.y = SZ;
        highp vec2 BZ;
        BZ = (texCoord + GZ);
        lowp float cZ;
        cZ = (1.0 - ((texture2D (blurmap0, rZ).w * texture2D (blurmap0, _Z).w) * (texture2D (blurmap0, oZ).w * texture2D (blurmap0, BZ).w)));
        WW = cZ;
        highp float dZ;
        dZ = clamp (mix ((
          (x * 10.0)
         * oW), (
          (x * 10.0)
         * 
          pow (oW, 4.0)
        ), (1.0 - x)), 0.0, 1.0);
        oW = dZ;
        highp float eZ;
        eZ = clamp (mix ((
          (x * 10.0)
         * ZW), (
          (x * 10.0)
         * 
          pow (ZW, 4.0)
        ), (1.0 - x)), 0.0, 1.0);
        ZW = eZ;
        highp float fZ;
        fZ = clamp (mix ((
          (x * 10.0)
         * WW), (
          (x * 10.0)
         * 
          pow (WW, 4.0)
        ), (1.0 - x)), 0.0, 1.0);
        WW = fZ;
        XW = (((1.0 - 
          ((0.5 * (1.0 - (
            clamp ((dZ - fZ), 0.0, 1.0)
           * 0.4))) * (1.0 - (clamp (
            (fZ - dZ)
          , 0.0, 1.0) * 0.4)))
        ) * (
          ((1.0 - clamp ((eZ - dZ), 0.0, 1.0)) * 0.4)
         + 0.6)) * ((
          (1.0 - clamp ((dZ - eZ), 0.0, 1.0))
         * 0.4) + 0.6));
        highp vec3 hZ;
        hZ.x = XW;
        hZ.y = XW;
        hZ.z = XW;
        y = hZ;
      };
    };
  };
  highp float jZ;
  if ((y.x < 0.5)) {
    jZ = max (((g.x + 
      (2.0 * y.x)
    ) - 1.0), 0.0);
  } else {
    jZ = min ((g.x + (2.0 * 
      (y.x - 0.5)
    )), 1.0);
  };
  highp float kZ;
  if ((y.y < 0.5)) {
    kZ = max (((g.y + 
      (2.0 * y.y)
    ) - 1.0), 0.0);
  } else {
    kZ = min ((g.y + (2.0 * 
      (y.y - 0.5)
    )), 1.0);
  };
  highp float mZ;
  if ((y.z < 0.5)) {
    mZ = max (((g.z + 
      (2.0 * y.z)
    ) - 1.0), 0.0);
  } else {
    mZ = min ((g.z + (2.0 * 
      (y.z - 0.5)
    )), 1.0);
  };
  highp vec3 nZ;
  nZ.x = jZ;
  nZ.y = kZ;
  nZ.z = mZ;
  mediump vec4 pZ;
  pZ.w = 1.0;
  pZ.xyz = nZ;
  gl_FragColor = pZ;
}

