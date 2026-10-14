uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D blurmap2;
uniform sampler2D overlay;
uniform highp vec2 randomOffset;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
uniform mediump vec3 outlineColor;
void main ()
{
  mediump vec4 r;
  mediump vec4 y;
  mediump vec4 g;
  mediump vec3 x;
  lowp vec3 b;
  b = texture2D (tex, texCoord).xyz;
  x = b;
  lowp vec4 z;
  z = texture2D (outline, texCoord);
  g = z;
  lowp vec4 a;
  a = texture2D (blurmap2, texCoord);
  y = a;
  lowp vec4 _;
  highp vec2 A;
  A = (texCoordTransformed + randomOffset);
  _ = texture2D (overlay, A);
  r = _;
  mediump vec4 Y;
  Y.w = 1.0;
  Y.xyz = clamp (((y.xyz + 
    ((mix (outlineColor, x, g.y) - y.xyz) * 0.55)
  ) + vec3((
    dot (r.xyz, r.xyz)
   * 0.1))), 0.0, 1.0);
  gl_FragColor = Y;
}

