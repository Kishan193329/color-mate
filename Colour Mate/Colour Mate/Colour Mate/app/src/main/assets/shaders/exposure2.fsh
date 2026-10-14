uniform sampler2D tex;
uniform sampler2D outline;
varying highp vec2 texCoord;
void main ()
{
  lowp vec3 r;
  r = mix (texture2D (tex, texCoord).xyz, vec3(0.5, 0.5, 0.5), 0.5);
  lowp vec3 y;
  y = texture2D (outline, texCoord).xyz;
  lowp float g;
  if ((r.x < 0.5)) {
    g = max (((y.x + 
      (2.0 * r.x)
    ) - 1.0), 0.0);
  } else {
    g = min ((y.x + (2.0 * 
      (r.x - 0.5)
    )), 1.0);
  };
  lowp float x;
  if ((r.y < 0.5)) {
    x = max (((y.y + 
      (2.0 * r.y)
    ) - 1.0), 0.0);
  } else {
    x = min ((y.y + (2.0 * 
      (r.y - 0.5)
    )), 1.0);
  };
  lowp float b;
  if ((r.z < 0.5)) {
    b = max (((y.z + 
      (2.0 * r.z)
    ) - 1.0), 0.0);
  } else {
    b = min ((y.z + (2.0 * 
      (r.z - 0.5)
    )), 1.0);
  };
  lowp vec3 z;
  z.x = g;
  z.y = x;
  z.z = b;
  mediump vec4 a;
  a.w = 1.0;
  a.xyz = z;
  gl_FragColor = a;
}

