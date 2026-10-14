uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D indexTex;
uniform sampler2D blurmap0;
uniform sampler2D noise;
uniform sampler2D hatch;
uniform sampler2D background;
uniform sampler2D animNoise;
varying highp vec2 texCoord;
uniform highp vec2 randomOffset;
uniform highp vec2 timeStrength;
void main ()
{
  mediump vec4 r;
  highp vec2 y;
  mediump vec4 g;
  mediump vec4 x;
  mediump vec4 b;
  mediump vec4 z;
  mediump vec4 a;
  lowp vec4 _;
  highp vec2 A;
  A = (texCoord * 3.0);
  _ = texture2D (background, A);
  a = _;
  lowp vec4 Y;
  highp vec2 R;
  R = (texCoord * 2.25);
  Y = texture2D (noise, R);
  z = Y;
  lowp vec4 X;
  X = texture2D (blurmap0, texCoord);
  b = X;
  lowp vec4 W;
  W = texture2D (outline, texCoord);
  x = W;
  lowp vec2 Z;
  Z = texture2D (indexTex, texCoord).xy;
  y = Z;
  highp float o;
  o = (((y.x * 256.0) + y.y) / 13.0);
  highp float O;
  O = cos(o);
  highp vec2 I;
  I.x = O;
  I.y = sin(o);
  highp vec2 i;
  i.x = I.y;
  i.y = -(O);
  lowp vec4 l;
  highp vec2 S;
  S = (((
    (texCoord.x * I)
   + 
    (texCoord.y * i)
  ) * 2.0) + randomOffset);
  l = texture2D (hatch, S);
  r = l;
  lowp vec4 G;
  G = texture2D (tex, texCoord);
  g = G;
  lowp vec4 B;
  highp vec2 c;
  c = (texCoord * 1.5);
  B = texture2D (animNoise, c);
  highp vec4 d;
  d = mix (vec4(1.0, 1.0, 1.0, 1.0), g, mix (1.0, clamp (
    ((((timeStrength.x * 5.0) - 1.05) - ((
      dot ((texCoord - vec2(0.5, 0.5)), I)
     / 
      dot (I, I)
    ) + (
      (B.y - 0.5)
     * 0.4))) * 40.0)
  , 0.0, 1.0), timeStrength.y));
  g = d;
  g = (1.0 - ((1.0 - g) * 1.111111));
  mediump vec4 e;
  e.w = 1.0;
  e.xyz = (mix ((
    mix (g.xyz, vec3(1.0, 1.0, 1.0), (r.y + ((
      (1.0 - pow (b.w, 3.0))
     * 0.6) * z.y)))
   * 
    (1.0 + (x.y - 1.0))
  ), vec3(1.0, 1.0, 1.0), (1.0 - x.w)) * a.xyz);
  gl_FragColor = e;
}

