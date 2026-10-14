uniform sampler2D tex;
varying highp vec2 texCoord;
void main ()
{
  mediump vec4 r;
  lowp vec4 y;
  y = texture2D (tex, texCoord);
  r = y;
  gl_FragColor = r;
}

