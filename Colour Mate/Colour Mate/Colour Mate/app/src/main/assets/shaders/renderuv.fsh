varying mediump vec2 texCoord;
void main ()
{
  mediump vec4 r;
  r.zw = vec2(0.0, 1.0);
  r.xy = texCoord;
  gl_FragColor = r;
}

