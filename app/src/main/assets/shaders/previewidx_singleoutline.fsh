uniform sampler2D outline;
uniform sampler2D tex;
varying highp vec2 texCoord;
void main ()
{
  mediump vec3 r;
  mediump vec4 y;
  lowp vec4 g;
  g = texture2D (outline, texCoord);
  y = g;
  highp vec2 x;
  x = (texCoord + vec2(0.0002441406, 0.0002441406));
  lowp vec3 b;
  b = texture2D (tex, x).xyz;
  r = b;
  mediump vec4 z;
  z.w = 1.0;
  z.xyz = (r * y.xyz);
  gl_FragColor = z;
}

