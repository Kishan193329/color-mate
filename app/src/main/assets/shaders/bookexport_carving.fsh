uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D basetex;
uniform sampler2D blurmap1;
uniform sampler2D blurmap0;
uniform highp vec2 randomOffset;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
uniform mediump vec3 outlineColor;
void main ()
{
  mediump vec4 r;
  mediump float y;
  mediump vec4 g;
  mediump vec4 x;
  lowp vec4 b;
  b = texture2D (outline, texCoord);
  x = b;
  lowp vec4 z;
  z = texture2D (tex, texCoord);
  g = z;
  highp float a;
  highp vec2 _;
  _ = (texCoord + vec2(0.0, -0.001953125));
  lowp float A;
  A = mix (clamp ((
    (texture2D (blurmap0, _).w - 0.95)
   * 80.0), 0.0, 1.0), 1.0, clamp ((
    (texture2D (blurmap1, _).w - 0.95)
   * 80.0), 0.0, 1.0));
  a = A;
  highp float Y;
  highp vec2 R;
  R = (texCoord + vec2(0.0, 0.001953125));
  lowp float X;
  X = mix (clamp ((
    (texture2D (blurmap0, R).w - 0.95)
   * 80.0), 0.0, 1.0), 1.0, clamp ((
    (texture2D (blurmap1, R).w - 0.95)
   * 80.0), 0.0, 1.0));
  Y = X;
  highp vec3 W;
  W.yz = vec2(0.0, 0.5);
  W.x = (a - Y);
  highp float Z;
  Z = (1.0 + (normalize(W).x * 0.5));
  y = Z;
  lowp vec4 o;
  highp vec2 O;
  O = (texCoordTransformed + randomOffset);
  o = texture2D (basetex, O);
  r = o;
  mediump float I;
  I = ((0.5 - (
    (max (max (g.x, g.y), g.z) + min (min (g.x, g.y), g.z))
   / 2.0)) * 2.0);
  mediump vec3 i;
  i = mix (outlineColor, (g.xyz + (
    (I - clamp (I, -0.5, 0.5))
   * 0.125)), x.y);
  mediump float l;
  if ((i.x < 0.5)) {
    l = ((2.0 * i.x) * r.x);
  } else {
    l = (1.0 - ((2.0 * 
      (1.0 - i.x)
    ) * (1.0 - r.x)));
  };
  mediump float S;
  if ((i.y < 0.5)) {
    S = ((2.0 * i.y) * r.y);
  } else {
    S = (1.0 - ((2.0 * 
      (1.0 - i.y)
    ) * (1.0 - r.y)));
  };
  mediump float G;
  if ((i.z < 0.5)) {
    G = ((2.0 * i.z) * r.z);
  } else {
    G = (1.0 - ((2.0 * 
      (1.0 - i.z)
    ) * (1.0 - r.z)));
  };
  mediump vec3 B;
  B.x = l;
  B.y = S;
  B.z = G;
  mediump vec4 c;
  c.w = 1.0;
  c.xyz = (B * y);
  gl_FragColor = c;
}

