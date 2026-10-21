uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D pattern;
uniform sampler2D background;
uniform highp vec2 randomOffset;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
void main ()
{
  highp float y;
  highp float g;
  mediump vec4 x;
  mediump vec3 b;
  mediump vec3 z;
  mediump vec3 a;
  highp vec2 _;
  _ = (texCoordTransformed + randomOffset);
  lowp vec3 A;
  A = texture2D (background, _).xyz;
  a = A;
  lowp vec3 Y;
  Y = texture2D (outline, texCoord).xyz;
  z = Y;
  lowp vec3 R;
  R = texture2D (tex, texCoord).xyz;
  b = R;
  x.w = min (min ((1.0 - b.x), (1.0 - b.y)), (1.0 - b.z));
  x.x = (((1.0 - b.x) - x.w) / (1.0 - x.w));
  x.y = (((1.0 - b.y) - x.w) / (1.0 - x.w));
  x.z = (((1.0 - b.z) - x.w) / (1.0 - x.w));
  mediump float X;
  X = max (max (x.x, x.y), max (x.z, x.w));
  g = X;
  y = 0.0;
  for (highp int r = 0; r < 6; r++) {
    highp float W;
    W = (6.30318 * (float(r) / 6.0));
    highp vec2 Z;
    Z.x = cos(W);
    Z.y = sin(W);
    highp vec2 o;
    o = (Z * 2.0);
    highp vec2 O;
    O.x = o.y;
    O.y = -(o.x);
    highp vec2 I;
    I = ((vec2((
      float(r)
     * 0.1)) + (texCoord.x * o)) + (texCoord.y * O));
    lowp float i;
    i = dot (texture2D (pattern, I).xyz, vec3(0.333333, 0.333333, 0.333333));
    y = (y + ((1.0 - i) * clamp (
      ((g - (float(r) / 6.0)) * 6.0)
    , 0.0, 1.0)));
  };
  highp vec3 l;
  l = mix (a, b, y);
  a = (l * z);
  mediump vec4 S;
  S.w = 1.0;
  S.xyz = a;
  gl_FragColor = S;
}

