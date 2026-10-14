uniform sampler2D outline;
uniform sampler2D tex1;
uniform sampler2D tex2;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
uniform highp vec2 randomOffset;
void main ()
{
  highp vec2 r;
  r = (texCoordTransformed + randomOffset);
  lowp vec4 y;
  y = mix (texture2D (tex1, r), texture2D (tex2, r), texture2D (outline, texCoord).y);
  gl_FragColor = y;
}

