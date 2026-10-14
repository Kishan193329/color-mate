uniform sampler2D outline;
uniform sampler2D tex;
uniform sampler2D texPrev;
uniform sampler2D noise;
varying highp vec2 texCoord;
uniform mediump vec4 reveal;
varying highp vec3 outline2Coord;
uniform sampler2D outline2;
void main ()
{
  mediump float r;
  mediump float y;
  lowp vec3 g;
  lowp vec4 x;
  x.xyz = texture2D (outline2, outline2Coord.xy).xyz;
  highp vec2 b;
  b = (vec2(0.5, 0.5) - outline2Coord.xy);
  highp float z;
  z = clamp ((5.0 - (10.0 * 
    sqrt(dot (b, b))
  )), 0.0, 1.0);
  x.w = z;
  lowp vec4 a;
  a = texture2D (outline, texCoord);
  lowp vec4 _;
  highp vec2 A;
  A = (texCoord + vec2(0.0002441406, 0.0002441406));
  _ = texture2D (tex, A);
  highp vec2 Y;
  Y = (reveal.yx + ((texCoord * 2.0) / (0.5 + reveal.w)));
  lowp float R;
  R = texture2D (noise, Y).x;
  y = R;
  highp vec2 X;
  X = (texCoord - reveal.xy);
  highp float W;
  W = (((
    sqrt(dot (X, X))
   / reveal.z) - (reveal.z * 1.5)) - (y * 0.5));
  r = W;
  lowp vec4 Z;
  Z = texture2D (texPrev, A);
  mediump vec3 o;
  o = mix (_.xyz, Z.xyz, clamp ((
    (r * 12.0)
   + 0.5), 0.0, 1.0));
  g = o;
  highp vec4 O;
  O.w = 1.0;
  O.xyz = (g * mix (a.xyz, x.xyz, (x.w * outline2Coord.z)));
  gl_FragColor = O;
}

