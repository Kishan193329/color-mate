uniform sampler2D tex;
uniform sampler2D overlay;
uniform sampler2D normalMap;
varying highp vec2 texCoord;
uniform highp vec2 lightDir;
void main ()
{
  mediump vec4 r;
  mediump vec4 y;
  highp vec3 g;
  lowp vec3 x;
  x = normalize((texture2D (normalMap, texCoord).xyz - vec3(0.5, 0.5, 0.5)));
  g = x;
  lowp vec4 b;
  b = texture2D (overlay, texCoord);
  y = b;
  lowp vec4 z;
  z = texture2D (tex, texCoord);
  r = z;
  highp vec3 a;
  a.z = 0.0;
  a.xy = (lightDir - vec2(0.5, 0.5));
  highp float _;
  _ = (0.9 + (dot (g, 
    normalize(a)
  ) * 0.9));
  r = (r * _);
  mediump vec3 A;
  A = r.xyz;
  mediump vec3 Y;
  mediump float R;
  R = ((max (
    max (r.x, r.y)
  , r.z) + min (
    min (r.x, r.y)
  , r.z)) / 2.0);
  if ((R > 0.8)) {
    lowp float X;
    mediump float W;
    W = ((R - 0.8) * 0.25);
    X = W;
    Y = (r.xyz - vec3(X));
  } else {
    if ((R < 0.35)) {
      lowp float Z;
      mediump float o;
      o = ((0.35 - R) * 0.4);
      Z = o;
      Y = (r.xyz + vec3(Z));
    } else {
      Y = A;
    };
  };
  mediump float O;
  if ((Y.x < 0.5)) {
    O = ((2.0 * Y.x) * y.x);
  } else {
    O = (1.0 - ((2.0 * 
      (1.0 - Y.x)
    ) * (1.0 - y.x)));
  };
  mediump float I;
  if ((Y.y < 0.5)) {
    I = ((2.0 * Y.y) * y.y);
  } else {
    I = (1.0 - ((2.0 * 
      (1.0 - Y.y)
    ) * (1.0 - y.y)));
  };
  mediump float i;
  if ((Y.z < 0.5)) {
    i = ((2.0 * Y.z) * y.z);
  } else {
    i = (1.0 - ((2.0 * 
      (1.0 - Y.z)
    ) * (1.0 - y.z)));
  };
  mediump vec3 l;
  l.x = O;
  l.y = I;
  l.z = i;
  mediump vec4 S;
  S.w = 1.0;
  S.xyz = l;
  gl_FragColor = S;
}

