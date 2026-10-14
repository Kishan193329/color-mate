uniform sampler2D tex;
varying highp vec2 texCoord;
void main ()
{
  lowp vec4 r;
  r = texture2D (tex, texCoord);
  gl_FragColor = r;
}

