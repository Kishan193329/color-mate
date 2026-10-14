uniform sampler2D tex;
varying mediump vec2 texCoord;
void main ()
{
  mediump vec2 r;
  r = (texCoord * vec2(2048.0, 2048.0));
  mediump vec4 y;
  mediump vec2 g;
  g = (texCoord + vec2(-0.0004882812, -0.0004882812));
  mediump vec2 x;
  x = (texCoord + vec2(0.0004882812, -0.0004882812));
  mediump vec2 b;
  b = (texCoord + vec2(-0.0004882812, 0.0004882812));
  mediump vec2 z;
  z = (texCoord + vec2(0.0004882812, 0.0004882812));
  mediump vec2 a;
  lowp vec4 _;
  lowp vec4 A;
  A = texture2D (tex, texCoord);
  lowp float Y;
  Y = dot (texture2D (tex, g).xyz, vec3(0.299, 0.587, 0.114));
  lowp float R;
  R = dot (texture2D (tex, x).xyz, vec3(0.299, 0.587, 0.114));
  lowp float X;
  X = dot (texture2D (tex, b).xyz, vec3(0.299, 0.587, 0.114));
  lowp float W;
  W = dot (texture2D (tex, z).xyz, vec3(0.299, 0.587, 0.114));
  lowp float Z;
  Z = dot (A.xyz, vec3(0.299, 0.587, 0.114));
  lowp float o;
  o = min (min (Z, Y), min (min (R, X), W));
  lowp float O;
  O = max (max (Z, Y), max (max (R, X), W));
  a.x = ((X + W) - (Y + R));
  a.y = ((Y + X) - (R + W));
  lowp float I;
  I = max (((
    (Y + R)
   + 
    (X + W)
  ) * 0.03125), 0.0078125);
  a = (min (vec2(8.0, 8.0), max (vec2(-8.0, -8.0), 
    (a * (1.0/((min (
      abs(a.x)
    , 
      abs(a.y)
    ) + I))))
  )) * vec2(0.0004882812, 0.0004882812));
  mediump vec2 i;
  i = ((r * vec2(0.0004882812, 0.0004882812)) + (a * -0.1666667));
  mediump vec2 l;
  l = ((r * vec2(0.0004882812, 0.0004882812)) + (a * 0.1666667));
  lowp vec3 S;
  S = (0.5 * (texture2D (tex, i).xyz + texture2D (tex, l).xyz));
  mediump vec2 G;
  G = ((r * vec2(0.0004882812, 0.0004882812)) + (a * -0.5));
  mediump vec2 B;
  B = ((r * vec2(0.0004882812, 0.0004882812)) + (a * 0.5));
  lowp vec3 c;
  c = ((S * 0.5) + (0.25 * (texture2D (tex, G).xyz + texture2D (tex, B).xyz)));
  lowp float d;
  d = dot (c, vec3(0.299, 0.587, 0.114));
  if (((d < o) || (d > O))) {
    lowp vec4 e;
    e.xyz = S;
    e.w = A.w;
    _ = e;
  } else {
    lowp vec4 f;
    f.xyz = c;
    f.w = A.w;
    _ = f;
  };
  y = _;
  gl_FragColor = y;
}

