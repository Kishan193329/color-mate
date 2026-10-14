uniform sampler2D tex;
uniform sampler2D outline;
varying highp vec2 texCoord;
uniform highp vec2 randomOffset;
void main ()
{
  lowp float r;
  lowp float y;
  highp float g;
  g = (randomOffset.x * 0.333);
  y = g;
  highp float x;
  x = ((randomOffset.y * 0.333) + 0.333);
  r = x;
  lowp vec3 b;
  b = texture2D (tex, texCoord).xyz;
  lowp vec3 z;
  z = (y + ((texture2D (outline, texCoord).xyz * r) * (1.0 - y)));
  lowp float a;
  if ((z.x < 0.5)) {
    a = max (((b.x + 
      (2.0 * z.x)
    ) - 1.0), 0.0);
  } else {
    a = min ((b.x + (2.0 * 
      (z.x - 0.5)
    )), 1.0);
  };
  lowp float _;
  if ((z.y < 0.5)) {
    _ = max (((b.y + 
      (2.0 * z.y)
    ) - 1.0), 0.0);
  } else {
    _ = min ((b.y + (2.0 * 
      (z.y - 0.5)
    )), 1.0);
  };
  lowp float A;
  if ((z.z < 0.5)) {
    A = max (((b.z + 
      (2.0 * z.z)
    ) - 1.0), 0.0);
  } else {
    A = min ((b.z + (2.0 * 
      (z.z - 0.5)
    )), 1.0);
  };
  lowp vec3 Y;
  Y.x = a;
  Y.y = _;
  Y.z = A;
  mediump vec4 R;
  R.w = 1.0;
  R.xyz = Y;
  gl_FragColor = R;
}

