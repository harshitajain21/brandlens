import {describe,it,expect,vi,afterEach} from 'vitest';
import {render,screen,fireEvent,cleanup} from '@testing-library/react';
import App from './App';
afterEach(()=>{cleanup();vi.restoreAllMocks()});
describe('BrandLens core workspace',()=>{
 it('loads the brand kit from the API and offers the sample review',async()=>{
  vi.stubGlobal('fetch',vi.fn(async(url:string)=>url.includes('/brand-kit')?new Response(JSON.stringify({brand:'LumaLeaf',tagline:'A lighter footprint',rules:[],facts:[],aiAvailable:false}),{status:200}):new Response('[]',{status:200})));
  render(<App/>);expect(await screen.findByText('Demo workspace')).toBeInTheDocument();fireEvent.click(screen.getByRole('button',{name:/try sample draft/i}));expect((await screen.findByLabelText('Draft text') as HTMLTextAreaElement).value).toContain('50% lower');
 });
 it('validates empty drafts before making a review request',async()=>{
  vi.stubGlobal('fetch',vi.fn(async()=>new Response(JSON.stringify({brand:'LumaLeaf',tagline:'',rules:[],facts:[],aiAvailable:false}),{status:200})));
  render(<App/>);await screen.findByText('Demo workspace');fireEvent.click(screen.getAllByRole('button',{name:'New review'})[0]);expect(screen.getByRole('button',{name:/run brand review/i})).toBeDisabled();
 });
});
