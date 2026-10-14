uniform sampler2D floortex;
varying highp vec2 texCoord;
uniform highp vec4 color;
void main ()
{
  lowp vec4 r;
  r = texture2D (floortex, texCoord);
  gl_FragColor = (r * color);
}

