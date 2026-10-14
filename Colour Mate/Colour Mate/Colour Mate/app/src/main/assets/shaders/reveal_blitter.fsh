uniform sampler2D tex;
uniform sampler2D texPrev;
uniform sampler2D noise;
varying highp vec2 texCoord;
uniform mediump vec4 reveal;
void main ()
{
  mediump float r;
  mediump float y;
  mediump vec3 g;
  lowp vec3 x;
  x = texture2D (tex, texCoord).xyz;
  g = x;
  highp vec2 b;
  b = (reveal.yx + ((texCoord * 2.0) / (0.5 + reveal.w)));
  lowp float z;
  z = texture2D (noise, b).x;
  y = z;
  highp vec2 a;
  a = (texCoord - reveal.xy);
  highp float _;
  _ = (((
    sqrt(dot (a, a))
   / reveal.z) - (reveal.z * 1.5)) - (y * 0.5));
  r = _;
  lowp vec4 A;
  A = texture2D (texPrev, texCoord);
  mediump vec3 Y;
  Y = mix (g, A.xyz, clamp ((
    (r * 12.0)
   + 0.5), 0.0, 1.0));
  g = Y;
  mediump vec4 R;
  R.w = 1.0;
  R.xyz = Y;
  gl_FragColor = R;
}

