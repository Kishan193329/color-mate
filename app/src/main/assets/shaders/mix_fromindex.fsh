uniform sampler2D indexTex;
uniform sampler2D tex1;
uniform sampler2D tex2;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
uniform highp vec2 randomOffset;
void main ()
{
  highp vec3 r;
  lowp vec3 y;
  y = texture2D (indexTex, texCoord).xyz;
  r = y;
  lowp vec4 g;
  highp vec2 x;
  x = (texCoordTransformed + randomOffset);
  g = texture2D (tex1, x);
  lowp vec4 b;
  b = texture2D (tex2, x);
  highp vec4 z;
  z = mix (g, b, clamp ((
    ((fract(dot (r, vec3(1.0, 256.0, 1.0))) - 0.5) * 10.0)
   + 0.5), 0.0, 1.0));
  gl_FragColor = z;
}

