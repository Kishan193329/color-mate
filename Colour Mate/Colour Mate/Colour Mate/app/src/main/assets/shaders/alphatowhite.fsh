uniform sampler2D tex;
varying highp vec2 texCoord;
void main ()
{
  mediump vec4 r;
  lowp vec4 y;
  y = texture2D (tex, texCoord);
  r = y;
  mediump vec4 g;
  g.w = 1.0;
  g.xyz = mix (vec3(1.0, 1.0, 1.0), r.xyz, r.w);
  gl_FragColor = g;
}

