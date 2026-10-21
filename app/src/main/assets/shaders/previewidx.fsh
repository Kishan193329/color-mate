uniform sampler2D outline;
uniform sampler2D tex;
varying highp vec2 texCoord;
varying highp vec3 outline2Coord;
uniform sampler2D outline2;
void main ()
{
  lowp vec4 r;
  r.xyz = texture2D (outline2, outline2Coord.xy).xyz;
  highp vec2 y;
  y = (vec2(0.5, 0.5) - outline2Coord.xy);
  highp float g;
  g = clamp ((9.0 - (20.0 * 
    sqrt(dot (y, y))
  )), 0.0, 1.0);
  r.w = g;
  lowp vec4 x;
  x = texture2D (outline, texCoord);
  lowp vec4 b;
  highp vec2 z;
  z = (texCoord + vec2(0.0002441406, 0.0002441406));
  b = texture2D (tex, z);
  highp vec4 a;
  a.w = 1.0;
  a.xyz = (b.xyz * mix (x.xyz, r.xyz, (r.w * outline2Coord.z)));
  gl_FragColor = a;
}

